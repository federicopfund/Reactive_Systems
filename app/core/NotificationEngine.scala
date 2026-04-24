package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import repositories.UserNotificationRepository
import services.EmailService
import models.UserNotification
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import java.time.Instant

/**
 * NotificationEngine — Hub centralizado de notificaciones reactivo con Circuit Breaker.
 *
 * Fan-out de notificaciones a múltiples canales:
 *   - In-app (persistidas en DB via UserNotificationRepository)
 *   - Email (via EmailService, SMTP — protegido por Circuit Breaker)
 *
 * Circuit Breaker Pattern (email channel):
 *   ┌────────┐  5 failures  ┌────────┐  60s timeout  ┌───────────┐
 *   │ CLOSED │─────────────▶│  OPEN  │──────────────▶│ HALF_OPEN │
 *   │(normal)│              │(reject)│               │  (test)   │
 *   └────────┘              └────────┘               └───────────┘
 *       ▲                                                 │
 *       │              success                            │
 *       └─────────────────────────────────────────────────┘
 *                          │ failure → back to OPEN
 *
 * Principios Reactivos:
 *   - Message-Driven: cada notificación es un comando independiente
 *   - Resilient: Circuit Breaker protege el canal email; in-app siempre funciona
 *   - Elastic: procesa alto volumen de notificaciones sin bloquear
 *   - Responsive: fire-and-forget, el caller no espera por delivery
 */

// ── Commands ──
sealed trait NotificationCommand

case class SendNotification(
  userId: Long,
  userEmail: Option[String],
  notificationType: String,
  title: String,
  message: String,
  publicationId: Option[Long],
  channels: Set[String], // "inapp", "email"
  replyTo: Option[ActorRef[NotificationResponse]]
) extends NotificationCommand

case class SendBulkNotification(
  userIds: Seq[Long],
  notificationType: String,
  title: String,
  message: String,
  publicationId: Option[Long]
) extends NotificationCommand

case class GetCircuitBreakerStatus(
  replyTo: ActorRef[NotificationResponse]
) extends NotificationCommand

private case class InAppNotifSaved(userId: Long, notifId: Long) extends NotificationCommand
private case class InAppNotifFailed(exception: Throwable, userId: Long) extends NotificationCommand
private case class EmailNotifSent(userId: Long, email: String) extends NotificationCommand
private case class EmailNotifFailed(exception: Throwable, userId: Long, email: String) extends NotificationCommand

// ── Responses ──
sealed trait NotificationResponse
case class NotificationQueued(channels: Set[String]) extends NotificationResponse
case class NotificationDeliveryError(reason: String) extends NotificationResponse
case class CircuitBreakerStatus(
  state: String,
  consecutiveFailures: Int,
  totalTripped: Int,
  emailsSkipped: Long
) extends NotificationResponse

object NotificationEngine {

  // ── Circuit Breaker State ──
  private case class EmailCircuitBreaker(
    state: String = "closed",           // "closed", "open", "half_open"
    consecutiveFailures: Int = 0,
    lastStateChange: Instant = Instant.now(),
    totalTripped: Int = 0,
    emailsSkipped: Long = 0
  ) {
    val FAILURE_THRESHOLD = 5
    val RESET_TIMEOUT_SECONDS = 60L
  }

  def apply(
    notificationRepo: UserNotificationRepository,
    emailService: EmailService
  )(implicit ec: ExecutionContext): Behavior[NotificationCommand] =
    Behaviors.setup { context =>
      context.log.info("[NotificationEngine] Started with Circuit Breaker (email protection)")
      active(notificationRepo, emailService, EmailCircuitBreaker())
    }

  private def active(
    notificationRepo: UserNotificationRepository,
    emailService: EmailService,
    cb: EmailCircuitBreaker
  )(implicit ec: ExecutionContext): Behavior[NotificationCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        case SendNotification(userId, userEmail, notificationType, title, msg, pubId, channels, replyTo) =>
          context.log.info(s"[NotificationEngine] Sending notification to user $userId via ${channels.mkString(", ")}")

          // ── In-app notification (always attempt, not affected by circuit breaker) ──
          if (channels.contains("inapp")) {
            val notification = UserNotification(
              userId = userId,
              notificationType = notificationType,
              title = title,
              message = msg,
              publicationId = pubId
            )
            context.pipeToSelf(notificationRepo.create(notification)) {
              case Success(id) => InAppNotifSaved(userId, id)
              case Failure(ex) => InAppNotifFailed(ex, userId)
            }
          }

          // ── Email notification (Circuit Breaker gated) ──
          val newCb = if (channels.contains("email")) {
            cb.state match {
              case "open" =>
                val elapsed = java.time.Duration.between(cb.lastStateChange, Instant.now())
                if (elapsed.getSeconds >= cb.RESET_TIMEOUT_SECONDS) {
                  // Transition OPEN → HALF_OPEN: test with one email
                  context.log.info(s"[NotificationEngine] ⚡ Circuit Breaker: OPEN → HALF_OPEN (testing email)")
                  userEmail.foreach { email =>
                    context.pipeToSelf(emailService.sendNotificationEmail(email, title, msg)) {
                      case Success(_)  => EmailNotifSent(userId, email)
                      case Failure(ex) => EmailNotifFailed(ex, userId, email)
                    }
                  }
                  cb.copy(state = "half_open", lastStateChange = Instant.now())
                } else {
                  val remaining = cb.RESET_TIMEOUT_SECONDS - elapsed.getSeconds
                  context.log.warn(s"[NotificationEngine] ⚡ Circuit Breaker OPEN — email skipped for user $userId (${remaining}s until test)")
                  cb.copy(emailsSkipped = cb.emailsSkipped + 1)
                }

              case _ => // "closed" or "half_open" — send normally
                userEmail match {
                  case Some(email) =>
                    context.pipeToSelf(emailService.sendNotificationEmail(email, title, msg)) {
                      case Success(_)  => EmailNotifSent(userId, email)
                      case Failure(ex) => EmailNotifFailed(ex, userId, email)
                    }
                  case None =>
                    context.log.warn(s"[NotificationEngine] No email for user $userId, skipping email channel")
                }
                cb
            }
          } else cb

          replyTo.foreach(_ ! NotificationQueued(channels))
          active(notificationRepo, emailService, newCb)

        case SendBulkNotification(userIds, notificationType, title, msg, pubId) =>
          context.log.info(s"[NotificationEngine] Bulk notification to ${userIds.size} users")
          userIds.foreach { userId =>
            val notification = UserNotification(
              userId = userId,
              notificationType = notificationType,
              title = title,
              message = msg,
              publicationId = pubId
            )
            context.pipeToSelf(notificationRepo.create(notification)) {
              case Success(id) => InAppNotifSaved(userId, id)
              case Failure(ex) => InAppNotifFailed(ex, userId)
            }
          }
          Behaviors.same

        // ═══════════════════════════════════════
        // Circuit Breaker Status Query
        // ═══════════════════════════════════════
        case GetCircuitBreakerStatus(replyTo) =>
          replyTo ! CircuitBreakerStatus(
            state = cb.state,
            consecutiveFailures = cb.consecutiveFailures,
            totalTripped = cb.totalTripped,
            emailsSkipped = cb.emailsSkipped
          )
          Behaviors.same

        case InAppNotifSaved(userId, notifId) =>
          context.log.info(s"[NotificationEngine] ✓ In-app notification $notifId saved for user $userId")
          Behaviors.same

        case InAppNotifFailed(ex, userId) =>
          context.log.error(s"[NotificationEngine] ✗ In-app notification failed for user $userId: ${ex.getMessage}")
          Behaviors.same

        // ═══════════════════════════════════════
        // Email success → Circuit Breaker recovery
        // ═══════════════════════════════════════
        case EmailNotifSent(userId, email) =>
          context.log.info(s"[NotificationEngine] ✓ Email sent to $email for user $userId")
          val newCb = if (cb.state == "half_open") {
            context.log.info(s"[NotificationEngine] ⚡ Circuit Breaker: HALF_OPEN → CLOSED (email recovered)")
            cb.copy(state = "closed", consecutiveFailures = 0, lastStateChange = Instant.now())
          } else {
            cb.copy(consecutiveFailures = 0)
          }
          active(notificationRepo, emailService, newCb)

        // ═══════════════════════════════════════
        // Email failure → Circuit Breaker trip
        // ═══════════════════════════════════════
        case EmailNotifFailed(ex, userId, email) =>
          context.log.error(s"[NotificationEngine] ✗ Email to $email failed for user $userId: ${ex.getMessage}")
          val failures = cb.consecutiveFailures + 1
          val newCb = if (cb.state == "half_open") {
            context.log.warn(s"[NotificationEngine] ⚡ Circuit Breaker: HALF_OPEN → OPEN (test failed)")
            cb.copy(state = "open", consecutiveFailures = failures, lastStateChange = Instant.now(), totalTripped = cb.totalTripped + 1)
          } else if (failures >= cb.FAILURE_THRESHOLD) {
            context.log.warn(s"[NotificationEngine] ⚡ Circuit Breaker: CLOSED → OPEN ($failures consecutive failures)")
            cb.copy(state = "open", consecutiveFailures = failures, lastStateChange = Instant.now(), totalTripped = cb.totalTripped + 1)
          } else {
            context.log.warn(s"[NotificationEngine] Email failure $failures/${cb.FAILURE_THRESHOLD} before circuit opens")
            cb.copy(consecutiveFailures = failures)
          }
          active(notificationRepo, emailService, newCb)
      }
    }
  }
}
