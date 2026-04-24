package controllers

import javax.inject._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import repositories.{UserRepository, BookmarkRepository, PublicationRepository}
import services.{EmailVerificationService, ReactiveAnalyticsAdapter, ReactiveNotificationAdapter}
import models.{User, Publication}
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

case class UserLoginForm(username: String, password: String, loginType: String)
case class UserRegisterForm(username: String, email: String, password: String, confirmPassword: String, fullName: String, registerAsAdmin: Boolean)
case class EmailVerificationForm(userId: Long, code: String)

@Singleton
class AuthController @Inject()( 
  cc: ControllerComponents,
  userRepository: UserRepository,
  bookmarkRepository: BookmarkRepository,
  publicationRepository: PublicationRepository,
  emailVerificationService: EmailVerificationService,
  analyticsAdapter: ReactiveAnalyticsAdapter,
  notificationAdapter: ReactiveNotificationAdapter,
  config: Configuration
)(implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {

  private val requireEmailVerification: Boolean = config.getOptional[Boolean]("auth.requireEmailVerification").getOrElse(true)

  // Formulario de login unificado
  val loginForm = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "loginType" -> nonEmptyText
    )(UserLoginForm.apply)(UserLoginForm.unapply)
  )

  // Formulario de registro
  val registerForm = Form(
    mapping(
      "username" -> nonEmptyText(minLength = 3, maxLength = 50),
      "email" -> email,
      "password" -> nonEmptyText(minLength = 6),
      "confirmPassword" -> nonEmptyText(minLength = 6),
      "fullName" -> nonEmptyText,
      "registerAsAdmin" -> boolean
    )(UserRegisterForm.apply)(UserRegisterForm.unapply)
      .verifying("Las contraseñas no coinciden", fields => fields match {
        case form => form.password == form.confirmPassword
      })
  )

  // Formulario de verificación de email
  val verificationForm = Form(
    mapping(
      "userId" -> longNumber,
      "code" -> nonEmptyText(minLength = 3, maxLength = 3)
    )(EmailVerificationForm.apply)(EmailVerificationForm.unapply)
  )

  // Helpers de autenticación
  private def isUserAuthenticated(request: RequestHeader): Boolean = {
    request.session.get("userId").isDefined
  }

  private def isAdminAuthenticated(request: RequestHeader): Boolean = {
    request.session.get("userId").exists(_ => 
      request.session.get("userRole").contains("admin")
    )
  }

  private def withUserAuth(block: => Future[Result])(implicit request: RequestHeader): Future[Result] = {
    if (isUserAuthenticated(request)) {
      block
    } else {
      Future.successful(Redirect(routes.AuthController.loginPage()).withNewSession)
    }
  }

  /**
   * Página de login unificada (usuarios y admins)
   */
  def loginPage(): Action[AnyContent] = Action { implicit request =>
    // Si ya está logueado, redirigir según el rol
    request.session.get("userId") match {
      case Some(_) =>
        request.session.get("userRole") match {
          case Some("admin") => Redirect(routes.AdminController.dashboard(0, None))
          case _ => Redirect(routes.AuthController.userDashboard())
        }
      case None => Ok(views.html.auth.login(loginForm))
    }
  }

  /**
   * Procesar login unificado
   */
  def login(): Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.auth.login(formWithErrors)))
      },
      loginData => {
        loginData.loginType match {
          case "user" => authenticateUser(loginData.username, loginData.password)
          case "admin" => authenticateAdmin(loginData.username, loginData.password)
          case _ => Future.successful(BadRequest(views.html.auth.login(
            loginForm.withGlobalError("Tipo de usuario inválido")
          )))
        }
      }
    )
  }

  /**
   * Autenticar usuario (detecta también admin/super_admin y redirige)
   */
  private def authenticateUser(username: String, password: String)(implicit request: RequestHeader): Future[Result] = {
    userRepository.findByUsername(username).flatMap {
      case Some(user) if BCrypt.checkpw(password, user.passwordHash) =>
        // Si es admin/super_admin, redirigir al flujo de admin
        if (user.isSuperAdmin || user.isAdmin) {
          authenticateAdmin(username, password)
        } else if (user.isPendingAdmin) {
          Future.successful(
            Unauthorized(views.html.auth.login(loginForm.withGlobalError("Tu solicitud de administrador está pendiente de aprobación por el Super Admin")))
          )
        } else if (!user.emailVerified && requireEmailVerification) {
          // Usuario no verificado y la app requiere verificación: enviar código
          emailVerificationService.createAndSendCode(user.id.get, user.email).map { _ =>
            Redirect(routes.AuthController.verifyEmailPage(user.id.get))
              .flashing("info" -> "Por favor verifica tu email para continuar")
          }
        } else {
          // Usuario verificado o verificación no requerida: login normal
          userRepository.updateLastLogin(user.id.get).map { _ =>
            // Track login via AnalyticsEngine
            analyticsAdapter.trackEvent("user.login", Some(user.id.get), Map(
              "username" -> user.username,
              "role" -> user.role
            ))
            Redirect(routes.AuthController.userDashboard())
              .withSession("userId" -> user.id.get.toString, "username" -> user.username, "userRole" -> user.role)
              .flashing("success" -> s"Bienvenido, ${user.fullName}")
          }
        }
      case _ =>
        Future.successful(
          Unauthorized(views.html.auth.login(loginForm.withGlobalError("Credenciales inválidas")))
        )
    }
  }

  /**
   * Autenticar administrador (desde tabla users con role admin/super_admin)
   */
  private def authenticateAdmin(username: String, password: String)(implicit request: RequestHeader): Future[Result] = {
    userRepository.findAdminByUsername(username).flatMap {
      case Some(user) if BCrypt.checkpw(password, user.passwordHash) =>
        if (!user.adminApproved && user.role != "super_admin") {
          Future.successful(
            Unauthorized(views.html.auth.login(loginForm.withGlobalError("Tu cuenta de administrador aún no ha sido aprobada por el Super Admin")))
          )
        } else {
          userRepository.updateLastLogin(user.id.get).map { _ =>
            // Track admin login via AnalyticsEngine
            analyticsAdapter.trackEvent("admin.login", Some(user.id.get), Map(
              "username" -> user.username,
              "role" -> user.role
            ))
            Redirect(routes.AdminController.dashboard(0, None))
              .withSession(
                "userId" -> user.id.get.toString,
                "username" -> user.username,
                "userRole" -> user.role
              )
              .flashing("success" -> s"Bienvenido Admin, ${user.fullName}")
          }
        }
      case _ =>
        // Check if pending_admin trying to login
        userRepository.findByUsername(username).flatMap {
          case Some(user) if user.isPendingAdmin && BCrypt.checkpw(password, user.passwordHash) =>
            Future.successful(
              Unauthorized(views.html.auth.login(loginForm.withGlobalError("Tu solicitud de administrador está pendiente de aprobación")))
            )
          case _ =>
            Future.successful(
              Unauthorized(views.html.auth.login(loginForm.withGlobalError("Credenciales de administrador inválidas")))
            )
        }
    }
  }

  /**
   * Página de registro
   */
  def registerPage(): Action[AnyContent] = Action { implicit request =>
    if (isUserAuthenticated(request)) {
      Redirect(routes.AuthController.userDashboard())
    } else {
      Ok(views.html.auth.register(registerForm))
    }
  }

  /**
   * Procesar registro
   */
  def register(): Action[AnyContent] = Action.async { implicit request =>
    registerForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.auth.register(formWithErrors)))
      },
      registerData => {
        // Verificar si el usuario o email ya existe
        userRepository.usernameExists(registerData.username).flatMap { usernameExists =>
          if (usernameExists) {
            Future.successful(BadRequest(views.html.auth.register(
              registerForm.withError("username", "Este nombre de usuario ya está en uso")
            )))
          } else {
            userRepository.emailExists(registerData.email).flatMap { emailExists =>
              if (emailExists) {
                Future.successful(BadRequest(views.html.auth.register(
                  registerForm.withError("email", "Este email ya está registrado")
                )))
              } else {
                // Determinar el rol según la opción de registro
                val roleFuture: Future[(String, Boolean)] = if (registerData.registerAsAdmin) {
                  // Si no hay super_admin, el primero se convierte en super_admin
                  userRepository.hasSuperAdmin().map {
                    case false => ("super_admin", true)   // Primer admin = super_admin, auto-aprobado
                    case true  => ("pending_admin", false) // Los siguientes esperan aprobación
                  }
                } else {
                  Future.successful(("user", false))
                }

                roleFuture.flatMap { case (role, autoApproved) =>
                  val hashedPassword = BCrypt.hashpw(registerData.password, BCrypt.gensalt(10))
                  val newUser = User(
                    id = None,
                    username = registerData.username,
                    email = registerData.email,
                    passwordHash = hashedPassword,
                    fullName = registerData.fullName,
                    role = role,
                    isActive = true,
                    createdAt = Instant.now(),
                    lastLogin = None,
                    emailVerified = false,
                    adminApproved = autoApproved,
                    adminRequestedAt = if (registerData.registerAsAdmin) Some(Instant.now()) else None
                  )

                  userRepository.create(newUser).map { _ =>
                    // Track registration via AnalyticsEngine
                    analyticsAdapter.trackEvent("user.registered", None, Map(
                      "username" -> registerData.username,
                      "role" -> role
                    ))
                    val flashMsg = role match {
                      case "super_admin"  => "¡Registro exitoso! Eres el Super Administrador del sistema. Inicia sesión para acceder."
                      case "pending_admin" => "¡Registro exitoso! Tu solicitud de administrador está pendiente de aprobación por el Super Admin."
                      case _              => "Registro exitoso. Por favor inicia sesión para verificar tu email."
                    }
                    Redirect(routes.AuthController.loginPage())
                      .flashing("success" -> flashMsg)
                  }
                }
              }
            }
          }
        }
      }
    )
  }

  /**
   * Logout unificado
   */
  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect(routes.HomeController.index())
      .withNewSession
      .withHeaders(
        "Cache-Control" -> "no-cache, no-store, must-revalidate",
        "Pragma" -> "no-cache",
        "Expires" -> "0"
      )
      .discardingCookies(DiscardingCookie("PLAY_SESSION"))
  }

  /**
   * Dashboard de usuario
   */
  def userDashboard(): Action[AnyContent] = Action.async { implicit request =>
    withUserAuth {
      val userId = request.session.get("userId").get.toLong
      for {
        userOpt <- userRepository.findById(userId)
        result <- userOpt match {
          case Some(user) =>
            for {
              bookmarkedIds <- bookmarkRepository.getBookmarkedIds(userId)
              bookmarkedPubs <- publicationRepository.findByIds(bookmarkedIds.toSeq)
              userPubs <- publicationRepository.findByUserId(userId)
            } yield Ok(views.html.auth.userDashboard(user, bookmarkedPubs, userPubs))
          case None =>
            Future.successful(Redirect(routes.AuthController.loginPage()).withNewSession)
        }
      } yield result
    }
  }

  /**
   * Perfil de usuario
   */
  def userProfile(): Action[AnyContent] = Action.async { implicit request =>
    withUserAuth {
      val userId = request.session.get("userId").get.toLong
      userRepository.findById(userId).map {
        case Some(user) => Ok(views.html.auth.userProfile(user))
        case None => Redirect(routes.AuthController.loginPage()).withNewSession
      }
    }
  }

  /**
   * Página de verificación de email
   */
  def verifyEmailPage(userId: Long): Action[AnyContent] = Action.async { implicit request =>
    userRepository.findById(userId).map {
      case Some(user) =>
        Ok(views.html.auth.verifyEmail(user.email, userId, None))
      case None =>
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> "Usuario no encontrado")
    }
  }

  /**
   * Procesar verificación de código
   */
  def verifyEmailCode(): Action[AnyContent] = Action.async { implicit request =>
    verificationForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest("Formulario inválido"))
      },
      verificationData => {
        emailVerificationService.verifyCode(verificationData.userId, verificationData.code).flatMap {
          case Right(true) =>
            // Código válido, actualizar usuario y hacer login
            for {
              _ <- userRepository.updateEmailVerified(verificationData.userId, true)
              user <- userRepository.findById(verificationData.userId)
              _ <- userRepository.updateLastLogin(verificationData.userId)
            } yield {
              user match {
                case Some(u) =>
                  Redirect(routes.AuthController.userDashboard())
                    .withSession("userId" -> u.id.get.toString, "username" -> u.username, "userRole" -> u.role)
                    .flashing("success" -> "¡Email verificado exitosamente! Bienvenido")
                case None =>
                  Redirect(routes.AuthController.loginPage())
                    .flashing("error" -> "Error al verificar email")
              }
            }
          
          case Right(false) =>
            // Este caso no debería ocurrir según la lógica del servicio, pero lo manejamos por completitud
            userRepository.findById(verificationData.userId).map { user =>
              Ok(views.html.auth.verifyEmail(
                user.map(_.email).getOrElse(""),
                verificationData.userId,
                Some("Error inesperado al verificar el código")
              ))
            }
          
          case Left(error) =>
            // Código inválido o error
            userRepository.findById(verificationData.userId).map {
              case Some(user) =>
                Ok(views.html.auth.verifyEmail(user.email, verificationData.userId, Some(error)))
              case None =>
                Redirect(routes.AuthController.loginPage())
                  .flashing("error" -> "Usuario no encontrado")
            }
        }
      }
    )
  }

  /**
   * Reenviar código de verificación
   */
  def resendVerificationCode(userId: Long): Action[AnyContent] = Action.async { implicit request =>
    userRepository.findById(userId).flatMap {
      case Some(user) =>
        emailVerificationService.createAndSendCode(userId, user.email).map { _ =>
          Redirect(routes.AuthController.verifyEmailPage(userId))
            .flashing("success" -> "Código reenviado. Revisa tu email")
        }.recover {
          case ex: Exception =>
            Redirect(routes.AuthController.verifyEmailPage(userId))
              .flashing("error" -> s"Error al reenviar código: ${ex.getMessage}")
        }
      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Usuario no encontrado")
        )
    }
  }
}
