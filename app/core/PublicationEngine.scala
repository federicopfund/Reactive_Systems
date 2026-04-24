package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import repositories.{PublicationRepository, UserNotificationRepository}
import models.{Publication, UserNotification}
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

/**
 * PublicationEngine — Actor reactivo para el ciclo de vida de publicaciones.
 *
 * Maneja el flujo completo: creación → revisión → aprobación/rechazo,
 * emitiendo notificaciones en cada transición de estado.
 *
 * Principios Reactivos:
 *   - Message-Driven: comandos tipados para cada acción del ciclo
 *   - Resilient: errores aislados por publicación, sin afectar al sistema
 *   - Responsive: respuesta inmediata, notificaciones fire-and-forget
 */

// ── Commands ──
sealed trait PublicationCommand

case class CreatePublication(
  userId: Long,
  username: String,
  title: String,
  content: String,
  excerpt: Option[String],
  coverImage: Option[String],
  category: String,
  tags: Option[String],
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

case class ApprovePublication(
  publicationId: Long,
  adminId: Long,
  adminUsername: String,
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

case class RejectPublication(
  publicationId: Long,
  adminId: Long,
  adminUsername: String,
  reason: String,
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

private case class PublicationCreated(
  publicationId: Long,
  username: String,
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

private case class PublicationCreateFailed(
  exception: Throwable,
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

private case class PublicationStatusUpdated(
  publicationId: Long,
  newStatus: String,
  userId: Long,
  adminUsername: String,
  reason: Option[String],
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

private case class PublicationStatusUpdateFailed(
  exception: Throwable,
  replyTo: ActorRef[PublicationResponse]
) extends PublicationCommand

private case class PublicationNotified(publicationId: Long) extends PublicationCommand
private case class PublicationNotifyFailed(exception: Throwable, publicationId: Long) extends PublicationCommand

// ── Responses ──
sealed trait PublicationResponse
case class PublicationCreatedOk(publicationId: Long) extends PublicationResponse
case class PublicationApproved(publicationId: Long) extends PublicationResponse
case class PublicationRejected(publicationId: Long) extends PublicationResponse
case class PublicationError(reason: String) extends PublicationResponse

object PublicationEngine {

  def apply(
    publicationRepo: PublicationRepository,
    notificationRepo: UserNotificationRepository
  )(implicit ec: ExecutionContext): Behavior[PublicationCommand] =
    active(publicationRepo, notificationRepo)

  private def active(
    publicationRepo: PublicationRepository,
    notificationRepo: UserNotificationRepository
  )(implicit ec: ExecutionContext): Behavior[PublicationCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        // ── Create publication ──
        case CreatePublication(userId, username, title, content, excerpt, coverImage, category, tags, replyTo) =>
          context.log.info(s"[PublicationEngine] Creating publication '$title' for user $userId")

          val slug = title.toLowerCase
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .take(100)

          val pub = Publication(
            userId = userId,
            title = title,
            slug = slug,
            content = content,
            excerpt = excerpt,
            coverImage = coverImage,
            category = category,
            tags = tags,
            status = "pending"
          )

          context.pipeToSelf(publicationRepo.create(pub)) {
            case Success(id) => PublicationCreated(id, username, replyTo)
            case Failure(ex) => PublicationCreateFailed(ex, replyTo)
          }
          Behaviors.same

        case PublicationCreated(pubId, username, replyTo) =>
          context.log.info(s"[PublicationEngine] Publication $pubId created, notifying admins")
          replyTo ! PublicationCreatedOk(pubId)
          Behaviors.same

        case PublicationCreateFailed(ex, replyTo) =>
          context.log.error(s"[PublicationEngine] Failed to create publication: ${ex.getMessage}", ex)
          replyTo ! PublicationError(s"Error al crear publicación: ${ex.getMessage}")
          Behaviors.same

        // ── Approve publication ──
        case ApprovePublication(publicationId, adminId, adminUsername, replyTo) =>
          context.log.info(s"[PublicationEngine] Approving publication $publicationId by $adminUsername (id=$adminId)")

          context.pipeToSelf(
            publicationRepo.findById(publicationId).flatMap {
              case Some(pub) => publicationRepo.changeStatus(publicationId, "approved", adminId).map(_ => pub.userId)
              case None      => scala.concurrent.Future.failed(new NoSuchElementException(s"Publication $publicationId not found"))
            }
          ) {
            case Success(userId) => PublicationStatusUpdated(publicationId, "approved", userId, adminUsername, None, replyTo)
            case Failure(ex)     => PublicationStatusUpdateFailed(ex, replyTo)
          }
          Behaviors.same

        // ── Reject publication ──
        case RejectPublication(publicationId, adminId, adminUsername, reason, replyTo) =>
          context.log.info(s"[PublicationEngine] Rejecting publication $publicationId by $adminUsername (id=$adminId): $reason")
          context.pipeToSelf(
            publicationRepo.findById(publicationId).flatMap {
              case Some(pub) => publicationRepo.changeStatus(publicationId, "rejected", adminId, Some(reason)).map(_ => pub.userId)
              case None      => scala.concurrent.Future.failed(new NoSuchElementException(s"Publication $publicationId not found"))
            }
          ) {
            case Success(userId) => PublicationStatusUpdated(publicationId, "rejected", userId, adminUsername, Some(reason), replyTo)
            case Failure(ex)     => PublicationStatusUpdateFailed(ex, replyTo)
          }
          Behaviors.same

        // ── Status updated → notify author ──
        case PublicationStatusUpdated(publicationId, newStatus, userId, adminUsername, reason, replyTo) =>
          context.log.info(s"[PublicationEngine] Publication $publicationId → $newStatus")

          val (notifTitle, notifMessage) = newStatus match {
            case "approved" =>
              ("Publicación aprobada", s"Tu publicación ha sido aprobada por $adminUsername")
            case "rejected" =>
              ("Publicación rechazada", s"Tu publicación fue rechazada por $adminUsername: ${reason.getOrElse("Sin razón")}")
            case other =>
              (s"Estado actualizado: $other", s"Tu publicación cambió a estado: $other")
          }

          val notification = UserNotification(
            userId = userId,
            notificationType = "publication_status",
            title = notifTitle,
            message = notifMessage,
            publicationId = Some(publicationId)
          )

          context.pipeToSelf(notificationRepo.create(notification)) {
            case Success(_)  => PublicationNotified(publicationId)
            case Failure(ex) => PublicationNotifyFailed(ex, publicationId)
          }

          if (newStatus == "approved") replyTo ! PublicationApproved(publicationId)
          else replyTo ! PublicationRejected(publicationId)
          Behaviors.same

        case PublicationStatusUpdateFailed(ex, replyTo) =>
          context.log.error(s"[PublicationEngine] Status update failed: ${ex.getMessage}", ex)
          replyTo ! PublicationError(s"Error al actualizar estado: ${ex.getMessage}")
          Behaviors.same

        case PublicationNotified(publicationId) =>
          context.log.info(s"[PublicationEngine] Notification sent for publication $publicationId")
          Behaviors.same

        case PublicationNotifyFailed(ex, publicationId) =>
          context.log.error(s"[PublicationEngine] Notification failed for publication $publicationId: ${ex.getMessage}")
          Behaviors.same
      }
    }
  }
}
