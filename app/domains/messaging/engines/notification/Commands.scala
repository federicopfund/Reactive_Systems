package domains.messaging.engines.notification

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del NotificationEngine (hub de notificaciones + Circuit Breaker).
 *
 * Dos niveles de visibilidad:
 *   - Públicos → API para adapters y guardians.
 *   - Internos `private[engines]` → resultados asíncronos de entrega (in-app y email).
 */
sealed trait NotificationCommand

// ── API pública ──────────────────────────────────────────────────────────────

case class SendNotification(
  userId:           Long,
  userEmail:        Option[String],
  notificationType: String,
  title:            String,
  message:          String,
  publicationId:    Option[Long],
  channels:         Set[String], // "inapp", "email"
  replyTo:          Option[ActorRef[NotificationResponse]]
) extends NotificationCommand

case class SendBulkNotification(
  userIds:          Seq[Long],
  notificationType: String,
  title:            String,
  message:          String,
  publicationId:    Option[Long]
) extends NotificationCommand

case class GetCircuitBreakerStatus(
  replyTo: ActorRef[NotificationResponse]
) extends NotificationCommand

// ── Protocolo interno ────────────────────────────────────────────────────────

private[engines] case class InAppNotifSaved(userId: Long, notifId: Long) extends NotificationCommand
private[engines] case class InAppNotifFailed(exception: Throwable, userId: Long) extends NotificationCommand
private[engines] case class EmailNotifSent(userId: Long, email: String) extends NotificationCommand
private[engines] case class EmailNotifFailed(exception: Throwable, userId: Long, email: String) extends NotificationCommand
