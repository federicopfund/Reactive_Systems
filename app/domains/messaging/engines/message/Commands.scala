package domains.messaging.engines.message

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del MessageEngine (mensajería privada).
 *
 * Dos niveles de visibilidad:
 *   - [[SendPrivateMessage]] → API pública (adapters, guardian).
 *   - Internos `private[engines]` → pasos del pipeline de persistencia.
 */
sealed trait MessageCommand

// ── API pública ──────────────────────────────────────────────────────────────

case class SendPrivateMessage(
  senderId:         Long,
  senderUsername:   String,
  receiverId:       Long,
  publicationId:    Option[Long],
  publicationTitle: Option[String],
  subject:          String,
  content:          String,
  replyTo:          ActorRef[MessageResponse]
) extends MessageCommand

// ── Protocolo interno ────────────────────────────────────────────────────────

private[engines] case class MessagePersisted(
  messageId:        Long,
  receiverId:       Long,
  senderUsername:   String,
  publicationId:    Option[Long],
  publicationTitle: Option[String],
  subject:          String,
  replyTo:          ActorRef[MessageResponse]
) extends MessageCommand

private[engines] case class MessagePersistFailed(
  exception: Throwable,
  replyTo:   ActorRef[MessageResponse]
) extends MessageCommand

private[engines] case class NotificationCreated(messageId: Long) extends MessageCommand

private[engines] case class NotificationFailed(
  exception: Throwable,
  messageId: Long
) extends MessageCommand
