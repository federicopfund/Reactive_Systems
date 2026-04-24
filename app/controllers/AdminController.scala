package controllers

import javax.inject._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import repositories.{ContactRepository, UserRepository, PublicationRepository, PublicationFeedbackRepository, UserNotificationRepository, NewsletterRepository, PrivateMessageRepository, EditorialStageRepository, PublicationStageHistoryRepository}
import models.{ContactRecord, PublicationFeedback, UserNotification, FeedbackType, EditorialStageCode, PublicationStageHistory}
import services.{ReactivePublicationAdapter, ReactiveNotificationAdapter, ReactiveAnalyticsAdapter}
import core.{PublicationApproved, PublicationRejected, PublicationError}
import actions.{AdminOnlyAction, SuperAdminOnlyAction, AuthRequest}
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

case class LoginForm(username: String, password: String)
case class ContactForm(name: String, email: String, message: String)
case class ContactUpdateForm(id: Long, name: String, email: String, message: String, status: String)
case class PublicationReviewForm(publicationId: Long, action: String, rejectionReason: Option[String])

@Singleton
class AdminController @Inject()(
  cc: ControllerComponents,
  contactRepository: ContactRepository,
  userRepository: UserRepository,
  publicationRepository: PublicationRepository,
  feedbackRepository: PublicationFeedbackRepository,
  notificationRepository: UserNotificationRepository,
  newsletterRepository: NewsletterRepository,
  messageRepository: PrivateMessageRepository,
  stageRepository: EditorialStageRepository,
  stageHistoryRepository: PublicationStageHistoryRepository,
  publicationAdapter: ReactivePublicationAdapter,
  notificationAdapter: ReactiveNotificationAdapter,
  analyticsAdapter: ReactiveAnalyticsAdapter,
  adminAction: AdminOnlyAction,
  superAdminAction: SuperAdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  val loginForm = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6)
    )(LoginForm.apply)(LoginForm.unapply)
  )

  val contactForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "message" -> nonEmptyText
    )(ContactForm.apply)(ContactForm.unapply)
  )

  val contactUpdateForm = Form(
    mapping(
      "id" -> longNumber,
      "name" -> nonEmptyText,
      "email" -> email,
      "message" -> nonEmptyText,
      "status" -> nonEmptyText
    )(ContactUpdateForm.apply)(ContactUpdateForm.unapply)
  )

  /**
   * Página de login - Redirige al login unificado
   */
  def loginPage(): Action[AnyContent] = Action { implicit request =>
    Redirect(routes.AuthController.loginPage())
  }

  /**
   * Procesar login de admin (usa users table con roles admin/super_admin)
   */
  def login(): Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Error en el formulario. Por favor verifica los datos.")
        )
      },
      loginData => {
        userRepository.findAdminByUsername(loginData.username).flatMap {
          case Some(user) if BCrypt.checkpw(loginData.password, user.passwordHash) =>
            if (!user.adminApproved && user.role != "super_admin") {
              Future.successful(
                Redirect(routes.AuthController.loginPage())
                  .flashing("error" -> "Tu cuenta de administrador aún no ha sido aprobada por el Super Admin")
              )
            } else {
              userRepository.updateLastLogin(user.id.get).map { _ =>
                Redirect(routes.AdminController.dashboard(0, None))
                  .withSession(
                    "userId" -> user.id.get.toString,
                    "username" -> user.username,
                    "userRole" -> user.role
                  )
                  .flashing("success" -> s"Bienvenido, ${user.fullName}")
              }
            }
          case _ =>
            // Verificar si es un pending_admin
            userRepository.findByUsername(loginData.username).flatMap {
              case Some(user) if user.isPendingAdmin && BCrypt.checkpw(loginData.password, user.passwordHash) =>
                Future.successful(
                  Redirect(routes.AuthController.loginPage())
                    .flashing("error" -> "Tu solicitud de administrador está pendiente de aprobación")
                )
              case _ =>
                Future.successful(
                  Redirect(routes.AuthController.loginPage())
                    .flashing("error" -> "Credenciales de administrador inválidas")
                )
            }
        }
      }
    )
  }

  /**
   * Logout
   */
  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect(routes.HomeController.index())
      .withNewSession
      .flashing("success" -> "Sesión de administrador cerrada correctamente")
      .withHeaders(
        "Cache-Control" -> "no-cache, no-store, must-revalidate",
        "Pragma" -> "no-cache",
        "Expires" -> "0"
      )
      .discardingCookies(DiscardingCookie("PLAY_SESSION"))
  }

  /**
   * Dashboard principal
   */
  def dashboard(page: Int, search: Option[String]): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      contacts <- contactRepository.listAll()
      totalCount <- contactRepository.count()
      pendingPublications <- publicationRepository.findPending()
      pendingAdminsCount <- userRepository.countPendingAdmins()
      // Messaging metrics
      msgTotal <- messageRepository.countAll()
      msgUnread <- messageRepository.countAllUnread()
      msgRead <- messageRepository.countAllRead()
      msgWithPub <- messageRepository.countWithPublication()
      msgDirect <- messageRepository.countDirect()
      msgLast7 <- messageRepository.countInLastDays(7)
      msgLast30 <- messageRepository.countInLastDays(30)
      msgReadRate <- messageRepository.readRate()
      msgUniqueSenders <- messageRepository.countUniqueSenders()
      msgUniqueReceivers <- messageRepository.countUniqueReceivers()
      topSenders <- messageRepository.topSenders(5)
      topReceivers <- messageRepository.topReceivers(5)
      topPubsByMsg <- messageRepository.topPublicationsByMessages(5)
    } yield {
      val filteredContacts = search match {
        case Some(query) if query.nonEmpty =>
          contacts.filter(c =>
            c.name.toLowerCase.contains(query.toLowerCase) ||
            c.email.toLowerCase.contains(query.toLowerCase) ||
            c.message.toLowerCase.contains(query.toLowerCase)
          )
        case _ => contacts
      }
      
      val pageSize = 10
      val offset = page * pageSize
      val paginatedContacts = filteredContacts.slice(offset, offset + pageSize)
      val totalPages = Math.ceil(filteredContacts.length.toDouble / pageSize).toInt
      
      val msgMetrics = Map(
        "total" -> msgTotal,
        "unread" -> msgUnread,
        "read" -> msgRead,
        "withPublication" -> msgWithPub,
        "direct" -> msgDirect,
        "last7Days" -> msgLast7,
        "last30Days" -> msgLast30,
        "readRate" -> msgReadRate,
        "uniqueSenders" -> msgUniqueSenders,
        "uniqueReceivers" -> msgUniqueReceivers
      )
      Ok(views.html.admin.dashboard(paginatedContacts, request.username, page, totalPages, search, pendingPublications.length, pendingAdminsCount, request.role, msgMetrics, topSenders, topReceivers, topPubsByMsg))
    }
  }

  /**
   * Vista de estadísticas avanzadas
   */
  def statisticsPage(): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val username = request.username
    Future.successful(Ok(views.html.admin.statistics(username)))
  }

  /**
   * Ver detalle de un contacto
   */
  def viewContact(id: Long): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactRepository.findById(id).map {
      case Some(contact) => Ok(views.html.admin.contactDetail(contact))
      case None => NotFound("Contacto no encontrado")
    }
  }

  /**
   * Página para crear nuevo contacto
   */
  def createContactPage(): Action[AnyContent] = adminAction { implicit request: AuthRequest[AnyContent] =>
    Ok(views.html.admin.contactForm(contactForm, None))
  }

  /**
   * Crear nuevo contacto
   */
  def createContact(): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.admin.contactForm(formWithErrors, None)))
      },
      contactData => {
        val newContact = ContactRecord(
          id = None,
          name = contactData.name,
          email = contactData.email,
          message = contactData.message,
          createdAt = Instant.now(),
          status = "pending"
        )
        contactRepository.save(newContact).map { _ =>
          Redirect(routes.AdminController.dashboard(0, None))
            .flashing("success" -> "Contacto creado exitosamente")
        }
      }
    )
  }

  /**
   * Página para editar contacto
   */
  def editContactPage(id: Long): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactRepository.findById(id).map {
      case Some(contact) =>
        val filledForm = contactUpdateForm.fill(ContactUpdateForm(
          contact.id.get,
          contact.name,
          contact.email,
          contact.message,
          contact.status
        ))
        Ok(views.html.admin.contactEdit(filledForm, contact))
      case None => NotFound("Contacto no encontrado")
    }
  }

  /**
   * Actualizar contacto
   */
  def updateContact(id: Long): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactUpdateForm.bindFromRequest().fold(
      formWithErrors => {
        contactRepository.findById(id).map {
          case Some(contact) => BadRequest(views.html.admin.contactEdit(formWithErrors, contact))
          case None => NotFound("Contacto no encontrado")
        }
      },
      updateData => {
        val updatedContact = ContactRecord(
          id = Some(id),
          name = updateData.name,
          email = updateData.email,
          message = updateData.message,
          createdAt = Instant.now(),
          status = updateData.status
        )
        
        contactRepository.update(id, updatedContact).map { count =>
          if (count > 0) {
            Redirect(routes.AdminController.dashboard(0, None))
              .flashing("success" -> "Contacto actualizado exitosamente")
          } else {
            NotFound("Contacto no encontrado")
          }
        }
      }
    )
  }

  /**
   * Eliminar contacto
   */
  def deleteContact(id: Long): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactRepository.delete(id).map { count =>
      if (count > 0) {
        Redirect(routes.AdminController.dashboard(0, None))
          .flashing("success" -> "Contacto eliminado exitosamente")
      } else {
        NotFound("Contacto no encontrado")
      }
    }
  }

  /**
   * API JSON para actualizar estado rápidamente
   */
  def updateStatus(id: Long, status: String): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    contactRepository.updateStatus(id, status).map { count =>
      if (count > 0) {
        Ok(Json.obj("success" -> true, "message" -> "Estado actualizado"))
      } else {
        NotFound(Json.obj("success" -> false, "message" -> "Contacto no encontrado"))
      }
    }
  }

  /**
   * Estadísticas avanzadas para el dashboard profesional
   */
  def advancedStats(): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      // Estadísticas de usuarios
      totalUsers <- userRepository.count()
      allUsers <- userRepository.listAll()
      usersByRole <- userRepository.countByRole()
      usersLast7Days <- userRepository.getUsersRegisteredInLastDays(7)
      usersLast30Days <- userRepository.getUsersRegisteredInLastDays(30)
      activeUsersLast7Days <- userRepository.getActiveUsersInLastDays(7)
      neverLoggedIn <- userRepository.countNeverLoggedIn()

      // Estadísticas de contactos
      totalContacts <- contactRepository.count()
      allContacts <- contactRepository.listAll()
      contactsByStatus <- contactRepository.countByStatus()
      contactsLast7Days <- contactRepository.getContactsInLastDays(7)
      contactsLast30Days <- contactRepository.getContactsInLastDays(30)

      // Estadísticas de administradores (desde users table)
      approvedAdmins <- userRepository.findApprovedAdmins()
      pendingAdmins <- userRepository.countPendingAdmins()

    } yield {
        // Calcular métricas de tiempo promedio
        val now = Instant.now()
        val avgUserAge = if (allUsers.nonEmpty) {
          allUsers.map(u => java.time.Duration.between(u.createdAt, now).toDays).sum / allUsers.length
        } else 0L

        // Calcular tasa de activación (usuarios que han iniciado sesión)
        val activationRate = if (totalUsers > 0) {
          ((totalUsers - neverLoggedIn).toDouble / totalUsers * 100).round
        } else 0L

        // Calcular contactos por usuario activo
        val contactsPerActiveUser = if (activeUsersLast7Days.nonEmpty) {
          f"${contactsLast7Days.length.toDouble / activeUsersLast7Days.length}%.2f"
        } else "0"

        // Tendencia de crecimiento semanal
        val weeklyGrowth = if (usersLast30Days.length > usersLast7Days.length) {
          val monthlyRate = usersLast30Days.length / 4.0
          val weeklyRate = usersLast7Days.length.toDouble
          ((weeklyRate / monthlyRate - 1) * 100).round
        } else 0L

        // Tiempo promedio en la plataforma (días desde último login para usuarios activos)
        val avgDaysSinceLastLogin = if (activeUsersLast7Days.nonEmpty) {
          activeUsersLast7Days.map { user =>
            user.lastLogin.map(ll => java.time.Duration.between(ll, now).toDays).getOrElse(0L)
          }.sum / activeUsersLast7Days.length
        } else 0L

        // Preparar datos
        val pendingCount = contactsByStatus.get("pending").getOrElse(0)
        val processedCount = contactsByStatus.get("processed").getOrElse(0)
        val archivedCount = contactsByStatus.get("archived").getOrElse(0)
        val processingRateVal = if (totalContacts > 0) ((processedCount.toDouble / totalContacts * 100)).round.toInt else 0
        val efficiencyVal = if (totalContacts > 0) ((processedCount.toDouble / totalContacts * 100)).round.toInt else 0

        Ok(Json.obj(
          "users" -> Json.obj(
            "total" -> totalUsers,
            "newLast7Days" -> usersLast7Days.length,
            "newLast30Days" -> usersLast30Days.length,
            "activeLast7Days" -> activeUsersLast7Days.length,
            "neverLoggedIn" -> neverLoggedIn,
            "activationRate" -> activationRate.toInt,
            "avgAccountAgeDays" -> avgUserAge,
            "avgDaysSinceLastLogin" -> avgDaysSinceLastLogin,
            "byRole" -> Json.toJson(usersByRole),
            "weeklyGrowthPercent" -> weeklyGrowth.toInt
          ),
          "contacts" -> Json.obj(
            "total" -> totalContacts,
            "newLast7Days" -> contactsLast7Days.length,
            "newLast30Days" -> contactsLast30Days.length,
            "byStatus" -> Json.toJson(contactsByStatus),
            "pending" -> pendingCount,
            "processed" -> processedCount,
            "archived" -> archivedCount,
            "processingRate" -> processingRateVal,
            "contactsPerActiveUser" -> contactsPerActiveUser.toString
          ),
          "admins" -> Json.obj(
            "total" -> approvedAdmins.length,
            "pending" -> pendingAdmins,
            "recentActivity" -> approvedAdmins.count(a => a.lastLogin.exists(ll => 
              java.time.Duration.between(ll, now).toDays < 7
            ))
          ),
          "performance" -> Json.obj(
            "avgResponseTimeDays" -> (if (contactsLast30Days.nonEmpty) {
              contactsLast30Days.filter(_.status == "processed").map { c =>
                java.time.Duration.between(c.createdAt, now).toDays
              }.headOption.getOrElse(0L).toInt
            } else 0),
            "pendingBacklog" -> pendingCount,
            "efficiency" -> efficiencyVal
          )
        ))
      }
  }

  /**
   * API: Métricas del sistema de mensajería (JSON)
   */
  def messagingStats(): Action[AnyContent] = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      total <- messageRepository.countAll()
      unread <- messageRepository.countAllUnread()
      readCount <- messageRepository.countAllRead()
      withPub <- messageRepository.countWithPublication()
      direct <- messageRepository.countDirect()
      last7 <- messageRepository.countInLastDays(7)
      last30 <- messageRepository.countInLastDays(30)
      rate <- messageRepository.readRate()
      senders <- messageRepository.countUniqueSenders()
      receivers <- messageRepository.countUniqueReceivers()
      topS <- messageRepository.topSenders(5)
      topR <- messageRepository.topReceivers(5)
      topP <- messageRepository.topPublicationsByMessages(5)
    } yield {
      Ok(Json.obj(
        "total" -> total,
        "unread" -> unread,
        "read" -> readCount,
        "withPublication" -> withPub,
        "direct" -> direct,
        "last7Days" -> last7,
        "last30Days" -> last30,
        "readRate" -> rate,
        "uniqueSenders" -> senders,
        "uniqueReceivers" -> receivers,
        "topSenders" -> topS.map { case (id, name, count) => Json.obj("id" -> id, "username" -> name, "count" -> count) },
        "topReceivers" -> topR.map { case (id, name, count) => Json.obj("id" -> id, "username" -> name, "count" -> count) },
        "topPublications" -> topP.map { case (id, title, count) => Json.obj("id" -> id, "title" -> title, "count" -> count) }
      ))
    }
  }

  // ============================================
  // GESTIÓN DE PUBLICACIONES
  // ============================================

  /**
   * Ver publicaciones pendientes de aprobación
   */
  def pendingPublications = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      publications <- publicationRepository.findPending()
      stages       <- stageRepository.findActive()
    } yield Ok(views.html.admin.publicationReview(publications, stages))
  }

  /**
   * Listar todas las publicaciones con filtros
   */
  def allPublications(status: Option[String] = None) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      publications <- publicationRepository.findAllByStatus(status)
      stages       <- stageRepository.findActive()
    } yield Ok(views.html.admin.publicationsList(publications, status, stages))
  }

  /**
   * Ver detalle de una publicación para revisión.
   * Carga también la pipeline editorial (etapas + timeline) para que
   * todos los roles del backoffice vean el estado, y solo el rol
   * propietario de la etapa actual vea los botones de transición.
   */
  def reviewPublicationDetail(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    publicationRepository.findById(id).flatMap {
      case Some(publication) =>
        for {
          feedbacks    <- feedbackRepository.findByPublicationId(id)
          stages       <- stageRepository.findActive()
          timeline     <- stageHistoryRepository.timelineWithStageOf(id)
          currentEntry <- stageHistoryRepository.currentStageOf(id)
        } yield {
          val currentCode = currentEntry.flatMap(h => stages.find(_.id.contains(h.stageId)).map(_.code))
            .orElse(Some(EditorialStageCode.fromLegacyStatus(publication.status)))
          val nextStages = currentCode
            .map(c => utils.EditorialStagePolicy.nextStagesFor(c, request.role, stages))
            .getOrElse(Seq.empty)
          Ok(views.html.admin.publicationDetail(publication, feedbacks, stages, timeline, currentCode, nextStages, request.role))
        }
      case None =>
        Future.successful(NotFound("Publicación no encontrada"))
    }
  }

  /**
   * Transición editorial canónica: mueve una pieza de su etapa actual
   * a la `toStage` indicada en el formulario.
   *
   * Defensa en profundidad: valida `EditorialStagePolicy.canTransitionFrom`
   * y `isAllowedTarget` en el server además de la UI. Inserta una fila
   * en `publication_stage_history` (el trigger de DB cierra la previa),
   * actualiza el cache `current_stage_id`, sincroniza el `status` legado y
   * notifica al autor.
   */
  def transitionStage(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val form          = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val toCode        = form.get("toStage").flatMap(_.headOption).getOrElse("")
    val reason        = form.get("reason").flatMap(_.headOption).filter(_.trim.nonEmpty)
    val internalNotes = form.get("internalNotes").flatMap(_.headOption).filter(_.trim.nonEmpty)
    val redirectBack  = Redirect(routes.AdminController.reviewPublicationDetail(id))

    publicationRepository.findById(id).flatMap {
      case None =>
        Future.successful(redirectBack.flashing("error" -> "Publicación no encontrada"))

      case Some(pub) =>
        for {
          stages       <- stageRepository.findActive()
          currentEntry <- stageHistoryRepository.currentStageOf(id)
          currentCode = currentEntry.flatMap(h => stages.find(_.id.contains(h.stageId)).map(_.code))
            .getOrElse(EditorialStageCode.fromLegacyStatus(pub.status))
          targetStage = stages.find(_.code == toCode)
          ok = targetStage.isDefined &&
               utils.EditorialStagePolicy.canTransitionFrom(currentCode, request.role) &&
               utils.EditorialStagePolicy.isAllowedTarget(currentCode, toCode)
          result <- if (!ok) {
            Future.successful(redirectBack.flashing("error" ->
              s"Transición no permitida desde \"$currentCode\" a \"$toCode\" para tu rol"))
          } else {
            val target       = targetStage.get
            val legacyStatus = EditorialStageCode.toLegacyStatus(target.code)
            for {
              _ <- stageHistoryRepository.insertTransition(PublicationStageHistory(
                     publicationId = id,
                     stageId       = target.id.get,
                     enteredBy     = Some(request.userId),
                     reason        = reason,
                     internalNotes = internalNotes
                   ))
              _ <- publicationRepository.updateCurrentStage(id, target.id.get)
              _ <- publicationRepository.changeStatus(id, legacyStatus, request.userId,
                     reason.filter(_ => legacyStatus == "rejected"))
              _ <- notificationRepository.create(UserNotification(
                     userId           = pub.userId,
                     notificationType = "publication_status",
                     title            = s"Tu pieza pasó a: ${target.label}",
                     message          = s"${request.username} movió \"${pub.title}\" a la etapa \"${target.label}\"." +
                                        reason.map(r => s"\n\nMotivo: $r").getOrElse(""),
                     publicationId    = Some(id)
                   ))
              // ── Broadcast a suscriptores del newsletter ─────────────
              // Solo cuando la pieza alcanza publicación pública. Se notifica
              // in-app a cada suscriptor que además sea usuario registrado.
              // Excluye al propio autor para no duplicar la notificación anterior.
              _ <- if (target.code == EditorialStageCode.Published) {
                     for {
                       emails <- newsletterRepository.findActiveEmails()
                       recipients <- userRepository.findByEmails(emails)
                       ids = recipients.flatMap(_.id).filterNot(_ == pub.userId)
                       _ <- notificationRepository.createBroadcast(
                              userIds = ids,
                              notificationType = "community_publication",
                              title = s"Nueva pieza en la comunidad: ${pub.title}",
                              message = s"${request.username} publicó «${pub.title}». Léela ahora en la portada.",
                              publicationId = Some(id)
                            )
                     } yield ()
                   } else Future.successful(())
              _ = analyticsAdapter.trackEvent("publication.stage_transition", Some(request.userId), Map(
                    "publicationId" -> id.toString,
                    "from"          -> currentCode,
                    "to"            -> target.code,
                    "by"            -> request.username
                  ))
            } yield redirectBack.flashing("success" -> s"Publicación movida a ${target.label}")
          }
        } yield result
    }
  }

  /**
   * Aprobar una publicación — via PublicationEngine (Ask pattern)
   */
  def approvePublication(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    publicationAdapter.approvePublication(id, request.userId, request.username).map {
      case _: PublicationApproved =>
        // Track via AnalyticsEngine
        analyticsAdapter.trackEvent("publication.approved", Some(request.userId), Map(
          "publicationId" -> id.toString,
          "adminUsername" -> request.username
        ))
        Redirect(routes.AdminController.pendingPublications())
          .flashing("success" -> "Publicación aprobada exitosamente")
      case PublicationError(reason) =>
        BadRequest(s"Error al aprobar la publicación: $reason")
      case _ =>
        BadRequest("Error inesperado al aprobar la publicación")
    }
  }

  /**
   * Rechazar una publicación — via PublicationEngine (Ask pattern)
   */
  def rejectPublication(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val rejectionReason = request.body.asFormUrlEncoded
      .flatMap(_.get("rejectionReason"))
      .flatMap(_.headOption)
      .getOrElse("No cumple con los estándares de calidad")
    
    publicationAdapter.rejectPublication(id, request.userId, request.username, rejectionReason).map {
      case _: PublicationRejected =>
        analyticsAdapter.trackEvent("publication.rejected", Some(request.userId), Map(
          "publicationId" -> id.toString,
          "adminUsername" -> request.username,
          "reason" -> rejectionReason.take(100)
        ))
        Redirect(routes.AdminController.pendingPublications())
          .flashing("success" -> "Publicación rechazada")
      case PublicationError(reason) =>
        BadRequest(s"Error al rechazar la publicación: $reason")
      case _ =>
        BadRequest("Error inesperado al rechazar la publicación")
    }
  }

  /**
   * Eliminar una publicación (solo para admins)
   */
  def deletePublication(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    publicationRepository.deleteAsAdmin(id).map { success =>
      if (success) {
        Redirect(routes.AdminController.pendingPublications())
          .flashing("success" -> "Publicación eliminada exitosamente")
      } else {
        BadRequest("Error al eliminar la publicación")
      }
    }
  }

  /**
   * Guardar notas de admin sobre una publicación
   */
  def saveAdminNotes(id: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val notes = request.body.asFormUrlEncoded
      .flatMap(_.get("adminNotes"))
      .flatMap(_.headOption)
    
    publicationRepository.saveAdminNotes(id, notes).map { success =>
      if (success) {
        Redirect(routes.AdminController.reviewPublicationDetail(id))
          .flashing("success" -> "Notas guardadas exitosamente")
      } else {
        BadRequest("Error al guardar las notas")
      }
    }
  }

  /**
   * API: Listar todas las publicaciones (para admin)
   */
  def listAllPublicationsJson = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    publicationRepository.findPending().map { publications =>
      Ok(Json.toJson(publications.map { pubWithAuthor =>
        Json.obj(
          "id" -> pubWithAuthor.publication.id,
          "title" -> pubWithAuthor.publication.title,
          "author" -> pubWithAuthor.authorUsername,
          "status" -> pubWithAuthor.publication.status,
          "category" -> pubWithAuthor.publication.category,
          "createdAt" -> pubWithAuthor.publication.createdAt.toString,
          "updatedAt" -> pubWithAuthor.publication.updatedAt.toString
        )
      }))
    }
  }

  // ============================================
  // FEEDBACK ESTRUCTURADO (Admin → Usuario)
  // ============================================

  /**
   * Agregar feedback a una publicación
   */
  def addFeedback(publicationId: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val feedbackType = form.get("feedbackType").flatMap(_.headOption).getOrElse("general")
    val message = form.get("feedbackMessage").flatMap(_.headOption).getOrElse("")
    val sendNow = form.get("sendToUser").flatMap(_.headOption).contains("true")

    if (message.trim.isEmpty) {
      Future.successful(
        Redirect(routes.AdminController.reviewPublicationDetail(publicationId))
          .flashing("error" -> "El mensaje de feedback no puede estar vacío")
      )
    } else {
      val feedback = PublicationFeedback(
        publicationId = publicationId,
        adminId = request.userId,
        feedbackType = feedbackType,
        message = message.trim,
        sentToUser = sendNow
      )
      for {
        feedbackId <- feedbackRepository.create(feedback)
        pubOpt <- publicationRepository.findById(publicationId)
        _ = if (sendNow && pubOpt.isDefined) {
          val pub = pubOpt.get
          val typeLabel = FeedbackType.label(feedbackType)
          // Notify via NotificationEngine (fire-and-forget)
          notificationAdapter.notify(
            userId = pub.userId,
            userEmail = None,
            notificationType = "feedback_sent",
            title = s"Nuevo feedback: $typeLabel",
            message = message.trim.take(200),
            publicationId = Some(publicationId)
          )
        }
      } yield {
        val flashMsg = if (sendNow) "Feedback enviado al usuario" else "Feedback guardado (borrador)"
        Redirect(routes.AdminController.reviewPublicationDetail(publicationId))
          .flashing("success" -> flashMsg)
      }
    }
  }

  /**
   * Enviar un feedback existente al usuario (marcar como visible + crear notificación)
   */
  def sendFeedbackToUser(feedbackId: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val publicationId = request.body.asFormUrlEncoded
      .flatMap(_.get("publicationId"))
      .flatMap(_.headOption)
      .map(_.toLong)
      .getOrElse(0L)

    for {
      sent <- feedbackRepository.markAsSent(feedbackId)
      pubOpt <- if (sent) publicationRepository.findById(publicationId) else Future.successful(None)
      fbList <- if (sent) feedbackRepository.findByPublicationId(publicationId) else Future.successful(List.empty)
      _ = {
        val fbOpt = fbList.find(_.feedback.id.contains(feedbackId))
        if (sent && pubOpt.isDefined && fbOpt.isDefined) {
          val pub = pubOpt.get
          val fb = fbOpt.get.feedback
          val typeLabel = FeedbackType.label(fb.feedbackType)
          // Notify via NotificationEngine (fire-and-forget)
          notificationAdapter.notify(
            userId = pub.userId,
            userEmail = None,
            notificationType = "feedback_sent",
            title = s"Nuevo feedback: $typeLabel",
            message = fb.message.take(200),
            publicationId = Some(publicationId)
          )
        }
      }
    } yield {
      if (sent) {
        Redirect(routes.AdminController.reviewPublicationDetail(publicationId))
          .flashing("success" -> "Feedback enviado al usuario")
      } else {
        BadRequest("Error al enviar el feedback")
      }
    }
  }

  /**
   * Eliminar un feedback
   */
  def deleteFeedback(feedbackId: Long) = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    val publicationId = request.body.asFormUrlEncoded
      .flatMap(_.get("publicationId"))
      .flatMap(_.headOption)
      .map(_.toLong)
      .getOrElse(0L)

    feedbackRepository.delete(feedbackId).map { success =>
      if (success) {
        Redirect(routes.AdminController.reviewPublicationDetail(publicationId))
          .flashing("success" -> "Feedback eliminado")
      } else {
        BadRequest("Error al eliminar el feedback")
      }
    }
  }

  // Newsletter subscribers management
  def newsletterSubscribers() = adminAction.async { implicit request: AuthRequest[AnyContent] =>
    newsletterRepository.findAll().map { subscribers =>
      Ok(views.html.admin.newsletterSubscribers(subscribers))
    }
  }

  // ============================================
  // GESTIÓN DE ADMINISTRADORES (Super Admin Only)
  // ============================================

  /**
   * Panel de gestión de administradores — solo Super Admin
   */
  def adminManagement(): Action[AnyContent] = superAdminAction.async { implicit request: AuthRequest[AnyContent] =>
    for {
      pendingAdmins <- userRepository.findPendingAdmins()
      approvedAdmins <- userRepository.findApprovedAdmins()
    } yield {
      Ok(views.html.admin.adminManagement(pendingAdmins, approvedAdmins, request.username, request.role))
    }
  }

  /**
   * Aprobar un administrador pendiente asignándole un rol.
   *
   * El Super Admin elige el rol (admin | super_admin) en el formulario.
   * Tras aprobar se crea una `UserNotification` para que el nuevo admin
   * vea, en su bandeja, qué rol se le asignó y quién lo aprobó.
   */
  def approveAdmin(userId: Long): Action[AnyContent] = superAdminAction.async { implicit request: AuthRequest[AnyContent] =>
    val rawRole = request.body.asFormUrlEncoded
      .flatMap(_.get("role").flatMap(_.headOption))
      .getOrElse("editor_jefe")

    utils.RolePolicy.assignableRoles.find(_.key == rawRole) match {
      case None =>
        Future.successful(
          Redirect(routes.AdminController.adminManagement())
            .flashing("error" -> s"Rol no válido: $rawRole")
        )
      case Some(roleDef) =>
        userRepository.approveAdmin(userId, request.userId, roleDef.key).flatMap { updated =>
          if (updated > 0) {
            val capLines = roleDef.caps.toSeq.sortBy(_.label).map(c => s"• ${c.label}").mkString("\n")
            val notif = UserNotification(
              userId           = userId,
              notificationType = "admin_role_assigned",
              title            = s"Tu solicitud fue aprobada · rol: ${roleDef.label}",
              message          = s"${request.username} te aprobó como ${roleDef.label}.\n\n${roleDef.description}\n\nPermisos asignados:\n$capLines"
            )
            notificationRepository.create(notif).map { _ =>
              Redirect(routes.AdminController.adminManagement())
                .flashing("success" -> s"Administrador aprobado como ${roleDef.label} y notificado")
            }
          } else {
            Future.successful(
              Redirect(routes.AdminController.adminManagement())
                .flashing("error" -> "No se pudo aprobar el administrador")
            )
          }
        }
    }
  }

  /**
   * Cambiar el rol de un administrador ya aprobado.
   * Solo super_admin. Notifica al usuario con el nuevo set de capacidades.
   */
  def changeAdminRole(userId: Long): Action[AnyContent] = superAdminAction.async { implicit request: AuthRequest[AnyContent] =>
    if (userId == request.userId) {
      Future.successful(
        Redirect(routes.AdminController.adminManagement())
          .flashing("error" -> "No podés cambiar tu propio rol desde aquí")
      )
    } else {
      val rawRole = request.body.asFormUrlEncoded
        .flatMap(_.get("role").flatMap(_.headOption))
        .getOrElse("")

      utils.RolePolicy.assignableRoles.find(_.key == rawRole) match {
        case None =>
          Future.successful(
            Redirect(routes.AdminController.adminManagement())
              .flashing("error" -> s"Rol no válido: $rawRole")
          )
        case Some(roleDef) =>
          userRepository.changeAdminRole(userId, roleDef.key).flatMap { updated =>
            if (updated > 0) {
              val capLines = roleDef.caps.toSeq.sortBy(_.label).map(c => s"• ${c.label}").mkString("\n")
              val notif = UserNotification(
                userId           = userId,
                notificationType = "admin_role_assigned",
                title            = s"Tu rol fue actualizado a ${roleDef.label}",
                message          = s"${request.username} cambió tu rol a ${roleDef.label}.\n\n${roleDef.description}\n\nPermisos actuales:\n$capLines"
              )
              notificationRepository.create(notif).map { _ =>
                Redirect(routes.AdminController.adminManagement())
                  .flashing("success" -> s"Rol actualizado a ${roleDef.label} y usuario notificado")
              }
            } else {
              Future.successful(
                Redirect(routes.AdminController.adminManagement())
                  .flashing("error" -> "No se pudo cambiar el rol (¿usuario fuera del backoffice?)")
              )
            }
          }
      }
    }
  }

  /**
   * Rechazar un administrador pendiente (vuelve a usuario normal)
   * Notifica al usuario del rechazo.
   */
  def rejectAdmin(userId: Long): Action[AnyContent] = superAdminAction.async { implicit request: AuthRequest[AnyContent] =>
    userRepository.rejectAdmin(userId).flatMap { updated =>
      if (updated > 0) {
        val notif = UserNotification(
          userId           = userId,
          notificationType = "admin_role_rejected",
          title            = "Tu solicitud de administrador fue rechazada",
          message          = s"${request.username} revisó tu solicitud y no fue aprobada en este momento."
        )
        notificationRepository.create(notif).map { _ =>
          Redirect(routes.AdminController.adminManagement())
            .flashing("success" -> "Solicitud rechazada y usuario notificado")
        }
      } else {
        Future.successful(
          Redirect(routes.AdminController.adminManagement())
            .flashing("error" -> "No se pudo rechazar la solicitud")
        )
      }
    }
  }

  /**
   * Revocar permisos de administrador (vuelve a usuario normal)
   * Notifica al ex-administrador.
   */
  def revokeAdmin(userId: Long): Action[AnyContent] = superAdminAction.async { implicit request: AuthRequest[AnyContent] =>
    userRepository.revokeAdmin(userId).flatMap { updated =>
      if (updated > 0) {
        val notif = UserNotification(
          userId           = userId,
          notificationType = "admin_role_revoked",
          title            = "Tus permisos de administrador fueron revocados",
          message          = s"${request.username} revocó tus permisos. Volviste al rol estándar."
        )
        notificationRepository.create(notif).map { _ =>
          Redirect(routes.AdminController.adminManagement())
            .flashing("success" -> "Permisos revocados y usuario notificado")
        }
      } else {
        Future.successful(
          Redirect(routes.AdminController.adminManagement())
            .flashing("error" -> "No se pudieron revocar los permisos")
        )
      }
    }
  }
}