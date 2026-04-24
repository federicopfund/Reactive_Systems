package core.guardian

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import core._
import repositories.UserNotificationRepository
import services.{AgentSettingsLookup, EmailService}

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * CrossCutGuardian — Capa 2 (Issue #15).
 *
 * Supervisa los 3 actores transversales: Notification, Moderation, Analytics.
 *
 * Capacidad nueva: **circuit breaker de capa para Moderation**.
 * Cuando el hijo `moderation` acumula 3 terminaciones, el guardian abre el CB
 * y responde a todos los `ForwardModeration(ModerateContent(...))` con un
 * `ModerationResult(verdict = "pending_review")` inmediato. Tras 30s entra
 * en half-open y vuelve a reenviar al hijo real.
 */

sealed trait CrossCutGuardianCommand
final case class ForwardNotification(cmd: NotificationCommand)        extends CrossCutGuardianCommand
final case class ForwardModeration(cmd: ModerationCommand)            extends CrossCutGuardianCommand
final case class ForwardAnalytics(cmd: AnalyticsCommand)              extends CrossCutGuardianCommand
final case class GetCrossCutHealth(replyTo: ActorRef[GuardianHealth]) extends CrossCutGuardianCommand
final case class GetCrossCutRefs(replyTo: ActorRef[CrossCutRefs])     extends CrossCutGuardianCommand
private final case class CrossCutChildTerminated(name: String)        extends CrossCutGuardianCommand
private case object CrossCutPingTick                                  extends CrossCutGuardianCommand
private case object ModerationCBHalfOpen                              extends CrossCutGuardianCommand

final case class CrossCutRefs(
  notification: ActorRef[NotificationCommand],
  moderation:   ActorRef[ModerationCommand],
  analytics:    ActorRef[AnalyticsCommand]
)

object CrossCutGuardian {

  private case class Children(
    notification: ActorRef[NotificationCommand],
    moderation:   ActorRef[ModerationCommand],
    analytics:    ActorRef[AnalyticsCommand]
  )

  private case class Health(
    notification: ChildHealth,
    moderation:   ChildHealth,
    analytics:    ChildHealth,
    moderationCB: String = "closed"   // "closed" | "open" | "half_open"
  ) {
    def asMap: Map[String, ChildHealth] = Map(
      "notification" -> notification,
      "moderation"   -> moderation,
      "analytics"    -> analytics
    )
    def isHealthy: Boolean =
      asMap.values.forall(_.status == ChildStatus.Healthy) && moderationCB != "open"
  }

  def apply(
    notificationRepo: UserNotificationRepository,
    emailService:     EmailService,
    lookup:           AgentSettingsLookup = AgentSettingsLookup.Defaults
  )(implicit ec: ExecutionContext): Behavior[CrossCutGuardianCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("[CrossCutGuardian] Starting — spawning 3 cross-cutting children with backoff")

      val maxRestarts       = lookup.getInt("supervision.crosscut.maxRestarts")
      val resetBackoffAfter = lookup.getDuration("supervision.crosscut.resetBackoffSec")
      val minBackoff        = lookup.getDuration("backoff.crosscut.minSec")
      val maxBackoff        = lookup.getDuration("backoff.crosscut.maxSec")
      val heartbeatInterval = lookup.getDuration("heartbeat.crosscut.intervalSec")
      val cbThreshold       = lookup.getInt("cb.moderation.failureThreshold")
      val cbReset           = lookup.getDuration("cb.moderation.resetSec")

      def supervise[T](b: Behavior[T]): Behavior[T] =
        Behaviors
          .supervise(b)
          .onFailure[Throwable](
            SupervisorStrategy
              .restartWithBackoff(minBackoff, maxBackoff, randomFactor = 0.2)
              .withMaxRestarts(maxRestarts)
              .withResetBackoffAfter(resetBackoffAfter)
          )

      val children = Children(
        notification = ctx.spawn(supervise(NotificationEngine(notificationRepo, emailService)), "notification"),
        moderation   = ctx.spawn(supervise(ModerationEngine()),                                 "moderation"),
        analytics    = ctx.spawn(supervise(AnalyticsEngine()),                                  "analytics")
      )

      ctx.watchWith(children.notification, CrossCutChildTerminated("notification"))
      ctx.watchWith(children.moderation,   CrossCutChildTerminated("moderation"))
      ctx.watchWith(children.analytics,    CrossCutChildTerminated("analytics"))

      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(CrossCutPingTick, heartbeatInterval)
        active(timers, children, initialHealth(), lookup, cbThreshold, cbReset)
      }
    }

  private def initialHealth(): Health = {
    val now = Instant.now()
    def fresh(name: String) = ChildHealth(name, ChildStatus.Healthy, 0, None, -1, now)
    Health(fresh("notification"), fresh("moderation"), fresh("analytics"))
  }

  private def active(
    timers:      akka.actor.typed.scaladsl.TimerScheduler[CrossCutGuardianCommand],
    children:    Children,
    health:      Health,
    lookup:      AgentSettingsLookup,
    cbThreshold: Int,
    cbReset:     FiniteDuration
  ): Behavior[CrossCutGuardianCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case ForwardNotification(cmd) =>
          if (!lookup.getBool("engines.notification.enabled")) {
            ctx.log.warn("[CrossCutGuardian] DROP notification — kill-switch off")
          } else if (health.notification.status != ChildStatus.Dead) children.notification ! cmd
          else ctx.log.warn("[CrossCutGuardian] DROP notification cmd — child is Dead")
          Behaviors.same

        case ForwardModeration(cmd) =>
          if (!lookup.getBool("engines.moderation.enabled")) {
            ctx.log.warn("[CrossCutGuardian] DROP moderation — kill-switch off")
            cmd match {
              case ModerateContent(contentId, _, _, _, _, replyTo) =>
                replyTo ! ModerationResult(contentId, "pending_review", List("kill_switch_off"), 0.0)
              case _ =>
            }
          } else if (health.moderationCB == "open") {
            cmd match {
              case ModerateContent(contentId, _, _, _, _, replyTo) =>
                ctx.log.warn("[CrossCutGuardian] Moderation CB OPEN — fast-fail to pending_review")
                replyTo ! ModerationResult(contentId, "pending_review", List("cb_open"), 0.0)
              case _ =>
            }
          } else if (health.moderation.status == ChildStatus.Dead) {
            cmd match {
              case ModerateContent(contentId, _, _, _, _, replyTo) =>
                ctx.log.warn("[CrossCutGuardian] Moderation Dead — fast-fail to pending_review")
                replyTo ! ModerationResult(contentId, "pending_review", List("dead"), 0.0)
              case _ =>
            }
          } else {
            children.moderation ! cmd
          }
          Behaviors.same

        case ForwardAnalytics(cmd) =>
          if (!lookup.getBool("engines.analytics.enabled")) {
            ctx.log.warn("[CrossCutGuardian] DROP analytics — kill-switch off")
          } else if (health.analytics.status != ChildStatus.Dead) children.analytics ! cmd
          else ctx.log.warn("[CrossCutGuardian] DROP analytics cmd — child is Dead")
          Behaviors.same

        case GetCrossCutHealth(replyTo) =>
          replyTo ! GuardianHealth("cross-cutting", health.isHealthy, health.asMap)
          Behaviors.same

        case GetCrossCutRefs(replyTo) =>
          replyTo ! CrossCutRefs(children.notification, children.moderation, children.analytics)
          Behaviors.same

        case CrossCutChildTerminated(name) =>
          ctx.log.warn(s"[CrossCutGuardian] child '$name' terminated permanently")
          val updated0 = updateChild(health, name) { c =>
            val restarts = c.restarts + 1
            c.copy(
              status    = if (restarts >= 5) ChildStatus.Dead else ChildStatus.Degraded(restarts, "child terminated"),
              restarts  = restarts,
              lastError = Some("child terminated"),
              updatedAt = Instant.now()
            )
          }
          val updated =
            if (name == "moderation" && updated0.moderation.restarts >= cbThreshold && updated0.moderationCB == "closed") {
              ctx.log.warn(s"[CrossCutGuardian] Moderation CB → OPEN (will retry in ${cbReset.toSeconds}s)")
              timers.startSingleTimer(ModerationCBHalfOpen, cbReset)
              updated0.copy(moderationCB = "open")
            } else updated0
          active(timers, children, updated, lookup, cbThreshold, cbReset)

        case ModerationCBHalfOpen =>
          ctx.log.info("[CrossCutGuardian] Moderation CB → HALF_OPEN (next request goes through)")
          active(timers, children, health.copy(moderationCB = "closed"), lookup, cbThreshold, cbReset)

        case CrossCutPingTick =>
          val now = Instant.now()
          active(timers, children, health.copy(
            notification = health.notification.copy(updatedAt = now),
            moderation   = health.moderation.copy(updatedAt   = now),
            analytics    = health.analytics.copy(updatedAt    = now)
          ), lookup, cbThreshold, cbReset)
      }
    }

  private def updateChild(h: Health, name: String)(f: ChildHealth => ChildHealth): Health = name match {
    case "notification" => h.copy(notification = f(h.notification))
    case "moderation"   => h.copy(moderation   = f(h.moderation))
    case "analytics"    => h.copy(analytics    = f(h.analytics))
    case _              => h
  }
}
