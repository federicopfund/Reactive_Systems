package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import repositories.{PrivateMessageRepository, UserNotificationRepository}
import models.{PrivateMessage, UserNotification}
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

/**
 * Sistema de mensajería reactivo basado en Akka Typed.
 *
 * Implements the Message-Driven principle of the Reactive Manifesto:
 * - Asynchronous message passing between components
 * - Location transparency
 * - Isolation and loose coupling
 * - Back-pressure through the ask pattern
 *
 * Flow:
 *   1. Controller sends SendPrivateMessage command to this actor
 *   2. Actor persists the message asynchronously via repository
 *   3. On success, actor creates a UserNotification for the receiver
 *   4. Actor replies with MessageSent / MessageError to the caller
 */

// ── Commands ──
sealed trait MessageCommand
case class SendPrivateMessage(
  senderId: Long,
  senderUsername: String,
  receiverId: Long,
  publicationId: Option[Long],
  publicationTitle: Option[String],
  subject: String,
  content: String,
  replyTo: ActorRef[MessageResponse]
) extends MessageCommand

private case class MessagePersisted(
  messageId: Long,
  receiverId: Long,
  senderUsername: String,
  publicationId: Option[Long],
  publicationTitle: Option[String],
  subject: String,
  replyTo: ActorRef[MessageResponse]
) extends MessageCommand

private case class MessagePersistFailed(
  exception: Throwable,
  replyTo: ActorRef[MessageResponse]
) extends MessageCommand

private case class NotificationCreated(messageId: Long) extends MessageCommand
private case class NotificationFailed(exception: Throwable, messageId: Long) extends MessageCommand

// ── Responses ──
sealed trait MessageResponse
case class MessageSent(messageId: Long) extends MessageResponse
case class MessageError(reason: String) extends MessageResponse

/**
 * MessageEngine — Actor reactivo para el sistema de mensajería privada.
 *
 * Siguiendo el principio Message-Driven del Manifiesto Reactivo:
 * los componentes se comunican exclusivamente a través de mensajes asíncronos,
 * garantizando aislamiento, tolerancia a fallos y elasticidad.
 */
object MessageEngine {

  def apply(
    messageRepo: PrivateMessageRepository,
    notificationRepo: UserNotificationRepository
  )(implicit ec: ExecutionContext): Behavior[MessageCommand] =
    active(messageRepo, notificationRepo)

  private def active(
    messageRepo: PrivateMessageRepository,
    notificationRepo: UserNotificationRepository
  )(implicit ec: ExecutionContext): Behavior[MessageCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        // ── Step 1: Persist the private message ──
        case SendPrivateMessage(senderId, senderUsername, receiverId, pubId, pubTitle, subject, content, replyTo) =>
          context.log.info(s"[MessageEngine] Processing message from user $senderId to user $receiverId")

          val pm = PrivateMessage(
            senderId = senderId,
            receiverId = receiverId,
            publicationId = pubId,
            subject = subject,
            content = content
          )

          context.pipeToSelf(messageRepo.create(pm)) {
            case Success(id) =>
              MessagePersisted(id, receiverId, senderUsername, pubId, pubTitle, subject, replyTo)
            case Failure(ex) =>
              MessagePersistFailed(ex, replyTo)
          }
          Behaviors.same

        // ── Step 2: Message saved → create notification for receiver ──
        case MessagePersisted(messageId, receiverId, senderUsername, pubId, pubTitle, subject, replyTo) =>
          context.log.info(s"[MessageEngine] Message $messageId persisted, notifying user $receiverId")

          val notifTitle = s"Nuevo mensaje de $senderUsername"
          val notifMessage = pubTitle match {
            case Some(title) => s"$senderUsername te envió un mensaje sobre \"$title\": $subject"
            case None        => s"$senderUsername te envió un mensaje: $subject"
          }

          val notification = UserNotification(
            userId = receiverId,
            notificationType = "private_message",
            title = notifTitle,
            message = notifMessage,
            publicationId = pubId
          )

          context.pipeToSelf(notificationRepo.create(notification)) {
            case Success(_)  => NotificationCreated(messageId)
            case Failure(ex) => NotificationFailed(ex, messageId)
          }

          // Reply immediately — notification is fire-and-forget
          replyTo ! MessageSent(messageId)
          Behaviors.same

        // ── Step 3a: Notification created (log only) ──
        case NotificationCreated(messageId) =>
          context.log.info(s"[MessageEngine] Notification created for message $messageId")
          Behaviors.same

        // ── Step 3b: Notification failed (log error, message was already sent) ──
        case NotificationFailed(ex, messageId) =>
          context.log.error(s"[MessageEngine] Failed to create notification for message $messageId: ${ex.getMessage}")
          Behaviors.same

        // ── Persistence failure ──
        case MessagePersistFailed(ex, replyTo) =>
          context.log.error(s"[MessageEngine] Failed to persist message: ${ex.getMessage}", ex)
          replyTo ! MessageError(s"No se pudo enviar el mensaje: ${ex.getMessage}")
          Behaviors.same
      }
    }
  }
}
