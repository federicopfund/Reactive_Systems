package controllers

import akka.actor.typed.{ActorRef, Scheduler}
import core.guardian.{CrossCutGuardianCommand, DomainGuardianCommand, InfraGuardianCommand}
import controllers.actions.AdminOnlyAction
import models.{AgentSetting, AgentSettingCategory, AgentSettingType}
import play.api.libs.json._
import play.api.mvc._
import repositories.AgentSettingsRepository
import services.AgentSettingsService
import utils.{AgentSettingsCatalog, Capabilities, RolePolicy}

import java.time.Instant
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

/**
 * AdminAgentSettingsController — Configuración runtime de los agentes (Issue #21).
 *
 *   GET  /admin/agents/settings              → formulario agrupado
 *   POST /admin/agents/settings              → upsert por key + audit
 *   POST /admin/agents/settings/reset/:key   → borra override + audit
 *   GET  /admin/agents/settings/runtime.json → snapshot consumido por el dashboard
 */
@Singleton
class AdminAgentSettingsController @Inject()(
  cc:          ControllerComponents,
  repo:        AgentSettingsRepository,
  service:     AgentSettingsService,
  adminAction: AdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private def canView(role: String): Boolean   = RolePolicy.can(role, Capabilities.Cap.ObservabilityView) || RolePolicy.can(role, Capabilities.Cap.ObservabilityManage)
  private def canManage(role: String): Boolean = RolePolicy.can(role, Capabilities.Cap.ObservabilityManage)

  def view: Action[AnyContent] = adminAction.async { implicit request =>
    if (!canView(request.role)) {
      Future.successful(Redirect(routes.AdminController.dashboard(0, None)).flashing("error" -> "Sin permiso para ver settings de agentes"))
    } else {
      for {
        overrides <- repo.all()
        audit     <- repo.lastAudit(20)
      } yield {
        val overridesMap = overrides.map(s => s.key -> s).toMap
        Ok(views.html.admin.agentSettings(
          grouped     = AgentSettingsCatalog.grouped,
          overrides   = overridesMap,
          audit       = audit,
          canManage   = canManage(request.role),
          username    = request.username
        ))
      }
    }
  }

  def save: Action[AnyContent] = adminAction.async { implicit request =>
    if (!canManage(request.role)) {
      Future.successful(Redirect(routes.AdminAgentSettingsController.view()).flashing("error" -> "Sin permiso para modificar settings"))
    } else {
      val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val submitted: Seq[(String, String)] = form.toSeq.collect {
        case (k, vs) if k.startsWith("setting.") => k.stripPrefix("setting.") -> vs.headOption.getOrElse("")
      }

      // Para checkboxes (bool) ausentes en el form, asumimos "false".
      val boolDefaults: Seq[(String, String)] = AgentSettingsCatalog.all
        .filter(_.valueType == AgentSettingType.BoolT)
        .map(_.key -> "false")
      val merged: Map[String, String] = (boolDefaults ++ submitted).toMap

      // Solo persistimos los que difieren del default y validan OK.
      val current = service.runtimeSnapshot

      val ops: Seq[Future[Either[String, Unit]]] = AgentSettingsCatalog.all.map { d =>
        merged.get(d.key) match {
          case None => Future.successful(Right(()))
          case Some(rawVal) =>
            AgentSettingsCatalog.parseAndValidate(d, rawVal) match {
              case Left(err) => Future.successful(Left(s"${d.label}: $err"))
              case Right(v) =>
                val previous = current.get(d.key)
                if (v == d.defaultValue && previous.isDefined) {
                  // Vuelve al default → borrar override
                  repo.deleteOne(d.key, Some(request.userId), Some(request.username), previous).map(_ => Right(()))
                } else if (previous.contains(v)) {
                  Future.successful(Right(())) // sin cambio
                } else if (v == d.defaultValue) {
                  Future.successful(Right(())) // default y no había override → noop
                } else {
                  val s = AgentSetting(d.key, v, d.valueType, d.category, Instant.now(), Some(request.userId))
                  repo.upsert(s, previous, Some(request.userId), Some(request.username), "set").map(_ => Right(()))
                }
            }
        }
      }

      Future.sequence(ops).flatMap { results =>
        val errors = results.collect { case Left(e) => e }
        service.refresh().map { _ =>
          if (errors.isEmpty)
            Redirect(routes.AdminAgentSettingsController.view()).flashing("success" -> "Configuración aplicada correctamente.")
          else
            Redirect(routes.AdminAgentSettingsController.view()).flashing("error" -> errors.mkString(" · "))
        }
      }
    }
  }

  def resetOne(key: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (!canManage(request.role)) {
      Future.successful(Redirect(routes.AdminAgentSettingsController.view()).flashing("error" -> "Sin permiso"))
    } else {
      val previous = service.runtimeSnapshot.get(key)
      repo.deleteOne(key, Some(request.userId), Some(request.username), previous)
        .flatMap(_ => service.refresh())
        .map(_ => Redirect(routes.AdminAgentSettingsController.view()).flashing("success" -> s"'$key' restablecido al default."))
    }
  }

  def runtimeJson: Action[AnyContent] = adminAction { implicit request =>
    if (!canView(request.role)) Forbidden(Json.obj("error" -> "forbidden"))
    else {
      val snap = service.runtimeSnapshot
      val view = AgentSettingsCatalog.all.map { d =>
        val effective = snap.getOrElse(d.key, d.defaultValue)
        Json.obj(
          "key"       -> d.key,
          "value"     -> effective,
          "default"   -> d.defaultValue,
          "override"  -> snap.contains(d.key),
          "category"  -> d.category.key
        )
      }
      Ok(Json.obj("settings" -> Json.toJson(view)))
    }
  }
}
