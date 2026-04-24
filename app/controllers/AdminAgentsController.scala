package controllers

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import core.guardian._
import play.api.libs.json._
import play.api.mvc._
import controllers.actions.AdminOnlyAction
import utils.{Capabilities, RolePolicy}
import services.AgentSettingsService

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * AdminAgentsController — Dashboard de observabilidad de actores (Issue #21).
 *
 * Reusa los `Get*Health` de los 3 guardians (Issue #15) y los expone:
 *   - `GET /admin/agents`            → vista HTML con polling
 *   - `GET /admin/agents/health.json` → snapshot JSON consumido por la vista
 *
 * Visible solo para roles con `Capabilities.Cap.ObservabilityView`.
 */
@Singleton
class AdminAgentsController @Inject()(
  cc:          ControllerComponents,
  domain:      ActorRef[DomainGuardianCommand],
  crossCut:    ActorRef[CrossCutGuardianCommand],
  infra:       ActorRef[InfraGuardianCommand],
  settings:    AgentSettingsService,
  adminAction: AdminOnlyAction
)(implicit ec: ExecutionContext, scheduler: Scheduler) extends AbstractController(cc) {

  private implicit val timeout: Timeout = 3.seconds

  private def gatherAll(): Future[Seq[GuardianHealth]] = {
    val d = domain.ask[GuardianHealth](GetDomainHealth(_))
    val c = crossCut.ask[GuardianHealth](GetCrossCutHealth(_))
    val i = infra.ask[GuardianHealth](GetInfraHealth(_))
    Future.sequence(Seq(d, c, i))
  }

  private def requireCap(role: String): Option[Result] =
    if (RolePolicy.can(role, Capabilities.Cap.ObservabilityView)) None
    else Some(Results.Redirect(routes.AdminController.dashboard(0, None)).flashing("error" -> "No tenés permisos para ver el dashboard de agentes"))

  def dashboard: Action[AnyContent] = adminAction.async { implicit request =>
    requireCap(request.role) match {
      case Some(deny) => Future.successful(deny)
      case None       =>
        val polling = math.max(2, settings.getInt("dashboard.pollingSec"))
        Future.successful(Ok(views.html.admin.agentsDashboard(request.username, request.role, polling)))
    }
  }

  def healthJson: Action[AnyContent] = adminAction.async { implicit request =>
    requireCap(request.role) match {
      case Some(_) => Future.successful(Forbidden(Json.obj("error" -> "forbidden")))
      case None =>
        gatherAll().map { layers =>
          val payload = Json.obj(
            "ok"        -> layers.forall(_.healthy),
            "timestamp" -> java.time.Instant.now().toString,
            "layers"    -> Json.toJson(layers.map { l =>
              Json.obj(
                "layer"   -> l.layer,
                "healthy" -> l.healthy,
                "children" -> Json.toJson(l.children.toSeq.sortBy(_._1).map { case (name, h) =>
                  Json.obj(
                    "name"        -> name,
                    "status"      -> h.status.name,
                    "restarts"    -> h.restarts,
                    "lastError"   -> h.lastError,
                    "lastPingMs"  -> h.lastPingMs,
                    "updatedAt"   -> h.updatedAt.toString
                  )
                })
              )
            })
          )
          Ok(payload)
        }.recover { case ex =>
          ServiceUnavailable(Json.obj("ok" -> false, "error" -> ex.getMessage))
        }
    }
  }
}
