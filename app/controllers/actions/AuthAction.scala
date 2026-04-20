package controllers.actions

import javax.inject.Inject
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

/**
 * AuthAction - Verifica si un usuario está autenticado
 * 
 * Uso:
 *   def protectedRoute = AuthAction { implicit request =>
 *     Ok(s"Bienvenido, ${request.userId}")
 *   }
 */
case class AuthRequest[A](userId: Long, username: String, role: String, request: Request[A]) 
  extends WrappedRequest[A](request)

class AuthAction @Inject()(
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends ActionBuilder[AuthRequest, AnyContent] {

  override def invokeBlock[A](
    request: Request[A], 
    block: AuthRequest[A] => Future[Result]
  ): Future[Result] = {
    request.session.get("userId") match {
      case Some(userIdStr) =>
        val userId = userIdStr.toLong
        val username = request.session.get("username").getOrElse("Usuario")
        val role = request.session.get("userRole").getOrElse("user")
        block(AuthRequest(userId, username, role, request))
        
      case None =>
        // Redirigir a login con URL de retorno
        val redirectUrl = request.uri
        Future.successful(
          Results.Redirect(controllers.routes.AuthController.loginPage())
            .flashing(
              "error" -> "Debes iniciar sesión para acceder a este recurso",
              "redirectUrl" -> redirectUrl
            )
        )
    }
  }
}

/**
 * OptionalAuthAction - Acción opcional que puede incluir datos de usuario si está logueado
 * Útil para páginas que quieren mostrar contenido diferente según el estado de auth
 */
case class OptionalAuthRequest[A](
  userInfo: Option[(Long, String, String)], // (userId, username, role)
  request: Request[A]
) extends WrappedRequest[A](request)

class OptionalAuthAction @Inject()(
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends ActionBuilder[OptionalAuthRequest, AnyContent] {

  override def invokeBlock[A](
    request: Request[A],
    block: OptionalAuthRequest[A] => Future[Result]
  ): Future[Result] = {
    val userInfo = for {
      userIdStr <- request.session.get("userId")
      userId = userIdStr.toLong
      username <- request.session.get("username")
      role <- request.session.get("userRole")
    } yield (userId, username, role)
    
    block(OptionalAuthRequest(userInfo, request))
  }
}

/**
 * UserAction - Verifica que el usuario está autenticado y tiene rol de usuario o admin
 */
class UserAction @Inject()(
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends ActionBuilder[AuthRequest, AnyContent] {

  override def invokeBlock[A](
    request: Request[A], 
    block: AuthRequest[A] => Future[Result]
  ): Future[Result] = {
    request.session.get("userId") match {
      case Some(userIdStr) =>
        val userId = userIdStr.toLong
        val username = request.session.get("username").getOrElse("Usuario")
        val role = request.session.get("userRole").getOrElse("user")
        
        if (role == "user" || utils.RolePolicy.isBackoffice(role)) {
          block(AuthRequest(userId, username, role, request))
        } else {
          Future.successful(
            Results.Forbidden("No tienes permisos para acceder a este recurso")
          )
        }
        
      case None =>
        Future.successful(
          Results.Redirect(controllers.routes.AuthController.loginPage())
            .flashing("error" -> "Debes iniciar sesión para acceder")
        )
    }
  }
}

/**
 * AdminOnlyAction - Gate de acceso al backoffice.
 * Permite cualquier rol declarado como "staff" en `RolePolicy.backofficeRoleKeys`
 * (super_admin, editor_jefe, revisor, moderador, newsletter, analista, + legacy "admin").
 * La autorización fina por acción debe hacerse luego con `RolePolicy.can(role, cap)`.
 */
class AdminOnlyAction @Inject()(
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends ActionBuilder[AuthRequest, AnyContent] {

  override def invokeBlock[A](
    request: Request[A], 
    block: AuthRequest[A] => Future[Result]
  ): Future[Result] = {
    request.session.get("userId") match {
      case Some(userIdStr) =>
        val userId = userIdStr.toLong
        val username = request.session.get("username").getOrElse("Admin")
        val role = request.session.get("userRole").getOrElse("user")

        if (utils.RolePolicy.isBackoffice(role)) {
          block(AuthRequest(userId, username, role, request))
        } else {
          Future.successful(
            Results.Forbidden("Solo los administradores pueden acceder a este recurso")
          )
        }
        
      case None =>
        Future.successful(
          Results.Redirect(controllers.routes.AdminController.loginPage())
            .flashing("error" -> "Debes iniciar sesión como administrador")
        )
    }
  }
}

/**
 * SuperAdminOnlyAction - Verifica que el usuario es super administrador
 */
class SuperAdminOnlyAction @Inject()(
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends ActionBuilder[AuthRequest, AnyContent] {

  override def invokeBlock[A](
    request: Request[A], 
    block: AuthRequest[A] => Future[Result]
  ): Future[Result] = {
    request.session.get("userId") match {
      case Some(userIdStr) =>
        val userId = userIdStr.toLong
        val username = request.session.get("username").getOrElse("Admin")
        val role = request.session.get("userRole").getOrElse("user")
        
        if (role == "super_admin") {
          block(AuthRequest(userId, username, role, request))
        } else {
          Future.successful(
            Results.Forbidden("Solo el Super Administrador puede acceder a este recurso")
          )
        }
        
      case None =>
        Future.successful(
          Results.Redirect(controllers.routes.AdminController.loginPage())
            .flashing("error" -> "Debes iniciar sesión como administrador")
        )
    }
  }
}

/**
 * CapabilityCheck — helper para gates finos por capacidad dentro de un endpoint admin.
 *
 * Uso típico desde un endpoint que ya pasó por `adminAction`:
 *
 *   def deletePublication(id: Long) = adminAction.async { implicit request =>
 *     CapabilityCheck.require(request, utils.Capabilities.Cap.PublicationsDelete) {
 *       publicationRepo.delete(id).map(_ => Redirect(...))
 *     }
 *   }
 */
object CapabilityCheck {
  def require[A](
    request: AuthRequest[A],
    cap: utils.Capabilities.Cap
  )(block: => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    if (utils.RolePolicy.can(request.role, cap)) block
    else Future.successful(
      Results.Forbidden(
        s"Tu rol (${utils.RolePolicy.labelFor(request.role)}) no tiene la capacidad: ${cap.label}"
      )
    )
  }
}
