package controllers

import controllers.actions.AdminOnlyAction
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import repositories.EditorialIdentityRepository
import utils.{Capabilities, RolePolicy}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

/**
 * AdminEditorialIdentityController — backoffice de la identidad editorial
 * (Issue #21).
 *
 *   GET  /admin/editorial-identity        → lista los 6 bloques
 *   POST /admin/editorial-identity/:key   → actualiza un bloque (title, body_html,
 *                                            order_index, active)
 */
@Singleton
class AdminEditorialIdentityController @Inject()(
  cc:           ControllerComponents,
  identityRepo: EditorialIdentityRepository,
  adminAction:  AdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private case class IdentityForm(title: String, bodyHtml: String, orderIndex: Int, active: Boolean)

  private val identityForm: Form[IdentityForm] = Form(
    mapping(
      "title"      -> nonEmptyText(maxLength = 200),
      "bodyHtml"   -> nonEmptyText,
      "orderIndex" -> number(min = 0, max = 9999),
      "active"     -> boolean
    )(IdentityForm.apply)(IdentityForm.unapply)
  )

  private def canManage(role: String): Boolean =
    RolePolicy.can(role, Capabilities.Cap.EditorialIdentityManage)

  def list: Action[AnyContent] = adminAction.async { implicit request =>
    if (!canManage(request.role)) {
      Future.successful(
        Redirect(routes.AdminController.dashboard(0, None))
          .flashing("error" -> "Sin permiso para editar la identidad editorial")
      )
    } else {
      identityRepo.findAll().map { blocks =>
        Ok(views.html.admin.editorialIdentity(blocks, request.role, request.username))
      }
    }
  }

  def update(sectionKey: String): Action[AnyContent] = adminAction.async { implicit request =>
    if (!canManage(request.role)) {
      Future.successful(
        Redirect(routes.AdminEditorialIdentityController.list())
          .flashing("error" -> "Sin permiso para editar la identidad editorial")
      )
    } else {
      identityForm.bindFromRequest().fold(
        formWithErrors => Future.successful(
          Redirect(routes.AdminEditorialIdentityController.list())
            .flashing("error" -> s"Datos inválidos: ${formWithErrors.errors.map(_.message).mkString(", ")}")
        ),
        data => identityRepo.updateContent(
          sectionKey = sectionKey,
          title      = data.title.trim,
          bodyHtml   = data.bodyHtml.trim,
          orderIndex = data.orderIndex,
          active     = data.active
        ).map {
          case 0 => Redirect(routes.AdminEditorialIdentityController.list())
                      .flashing("error" -> s"No existe el bloque '$sectionKey'.")
          case _ => Redirect(routes.AdminEditorialIdentityController.list())
                      .flashing("success" -> s"Bloque '$sectionKey' actualizado.")
        }
      )
    }
  }
}
