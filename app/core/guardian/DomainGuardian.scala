package core.guardian

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import core._
import repositories.{ContactRepository, PrivateMessageRepository, UserNotificationRepository, PublicationRepository, BadgeRepository}
import services.AgentSettingsLookup

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * DomainGuardian — Capa 1 (Issue #15).
 *
 * Supervisa los 4 actores de lógica de dominio: Contact, Message, Publication,
 * Gamification. Aporta capacidades operacionales que antes no existían:
 *
 *   - Reinicio con backoff exponencial (`SupervisorStrategy.restartWithBackoff`).
 *   - Detección tipada de terminación vía `ctx.watchWith`.
 *   - Estado de salud por hijo (`Healthy | Degraded | Dead`) consultable.
 *   - Fast-fail: si un hijo está `Dead`, el guardian responde de inmediato
 *     al `replyTo` con un error en lugar de esperar a un Ask timeout.
 */

sealed trait DomainGuardianCommand
final case class ForwardContact(cmd: ContactCommand)               extends DomainGuardianCommand
final case class ForwardMessage(cmd: MessageCommand)               extends DomainGuardianCommand
final case class ForwardPublication(cmd: PublicationCommand)       extends DomainGuardianCommand
final case class ForwardGamification(cmd: GamificationCommand)     extends DomainGuardianCommand
final case class GetDomainHealth(replyTo: ActorRef[GuardianHealth]) extends DomainGuardianCommand
final case class GetDomainRefs(replyTo: ActorRef[DomainRefs])      extends DomainGuardianCommand
private final case class DomainChildTerminated(name: String)       extends DomainGuardianCommand
private case object DomainPingTick                                 extends DomainGuardianCommand

final case class DomainRefs(
  contact:      ActorRef[ContactCommand],
  message:      ActorRef[MessageCommand],
  publication:  ActorRef[PublicationCommand],
  gamification: ActorRef[GamificationCommand]
)

object DomainGuardian {

  private case class Children(
    contact:      ActorRef[ContactCommand],
    message:      ActorRef[MessageCommand],
    publication:  ActorRef[PublicationCommand],
    gamification: ActorRef[GamificationCommand]
  )

  private case class Health(
    contact:      ChildHealth,
    message:      ChildHealth,
    publication:  ChildHealth,
    gamification: ChildHealth
  ) {
    def asMap: Map[String, ChildHealth] = Map(
      "contact"      -> contact,
      "message"      -> message,
      "publication"  -> publication,
      "gamification" -> gamification
    )
    def isHealthy: Boolean = asMap.values.forall(_.status == ChildStatus.Healthy)
  }

  def apply(
    contactRepo:      ContactRepository,
    messageRepo:      PrivateMessageRepository,
    notificationRepo: UserNotificationRepository,
    publicationRepo:  PublicationRepository,
    badgeRepo:        BadgeRepository,
    lookup:           AgentSettingsLookup = AgentSettingsLookup.Defaults
  )(implicit ec: ExecutionContext): Behavior[DomainGuardianCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info("[DomainGuardian] Starting — spawning 4 domain children with backoff supervision")

      val maxRestarts       = lookup.getInt("supervision.domain.maxRestarts")
      val resetBackoffAfter = lookup.getDuration("supervision.domain.resetBackoffSec")
      val minBackoff        = lookup.getDuration("backoff.domain.minSec")
      val maxBackoff        = lookup.getDuration("backoff.domain.maxSec")
      val heartbeatInterval = lookup.getDuration("heartbeat.domain.intervalSec")

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
        contact      = ctx.spawn(supervise(ContactEngine(contactRepo)),                               "contact"),
        message      = ctx.spawn(supervise(MessageEngine(messageRepo, notificationRepo)),             "message"),
        publication  = ctx.spawn(supervise(PublicationEngine(publicationRepo, notificationRepo)),     "publication"),
        gamification = ctx.spawn(supervise(GamificationEngine(badgeRepo)),                            "gamification")
      )

      ctx.watchWith(children.contact,      DomainChildTerminated("contact"))
      ctx.watchWith(children.message,      DomainChildTerminated("message"))
      ctx.watchWith(children.publication,  DomainChildTerminated("publication"))
      ctx.watchWith(children.gamification, DomainChildTerminated("gamification"))

      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(DomainPingTick, heartbeatInterval)
        active(children, initialHealth(), lookup)
      }
    }

  private def initialHealth(): Health = {
    val now = Instant.now()
    def fresh(name: String) = ChildHealth(name, ChildStatus.Healthy, 0, None, -1, now)
    Health(fresh("contact"), fresh("message"), fresh("publication"), fresh("gamification"))
  }

  private def active(children: Children, health: Health, lookup: AgentSettingsLookup): Behavior[DomainGuardianCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        // ── Forwarding ────────────────────────────────────────────
        case ForwardContact(cmd) =>
          if (!lookup.getBool("engines.contact.enabled")) {
            ctx.log.warn("[DomainGuardian] DROP contact — kill-switch off")
          } else if (health.contact.status == ChildStatus.Dead) failContact(ctx, cmd)
          else children.contact ! cmd
          Behaviors.same

        case ForwardMessage(cmd) =>
          if (!lookup.getBool("engines.message.enabled")) {
            ctx.log.warn("[DomainGuardian] DROP message — kill-switch off")
          } else if (health.message.status != ChildStatus.Dead) children.message ! cmd
          else ctx.log.warn(s"[DomainGuardian] DROP message cmd — child is Dead")
          Behaviors.same

        case ForwardPublication(cmd) =>
          if (!lookup.getBool("engines.publication.enabled")) {
            ctx.log.warn("[DomainGuardian] DROP publication — kill-switch off")
          } else if (health.publication.status != ChildStatus.Dead) children.publication ! cmd
          else ctx.log.warn(s"[DomainGuardian] DROP publication cmd — child is Dead")
          Behaviors.same

        case ForwardGamification(cmd) =>
          if (!lookup.getBool("engines.gamification.enabled")) {
            ctx.log.warn("[DomainGuardian] DROP gamification — kill-switch off")
          } else if (health.gamification.status != ChildStatus.Dead) children.gamification ! cmd
          else ctx.log.warn(s"[DomainGuardian] DROP gamification cmd — child is Dead")
          Behaviors.same

        // ── Health query ──────────────────────────────────────────
        case GetDomainHealth(replyTo) =>
          replyTo ! GuardianHealth("domain", health.isHealthy, health.asMap)
          Behaviors.same

        case GetDomainRefs(replyTo) =>
          replyTo ! DomainRefs(children.contact, children.message, children.publication, children.gamification)
          Behaviors.same

        // ── Termination handling ──────────────────────────────────
        case DomainChildTerminated(name) =>
          ctx.log.warn(s"[DomainGuardian] child '$name' terminated permanently — marking Dead")
          val updated = updateChild(health, name) { c =>
            val restarts = c.restarts + 1
            c.copy(
              status     = if (restarts >= 5) ChildStatus.Dead else ChildStatus.Degraded(restarts, "child terminated"),
              restarts   = restarts,
              lastError  = Some("child terminated"),
              updatedAt  = Instant.now()
            )
          }
          active(children, updated, lookup)

        // ── Periodic mailbox ping (timer) ─────────────────────────
        case DomainPingTick =>
          ctx.log.debug("[DomainGuardian] ping tick — health snapshot dispatched")
          val now = Instant.now()
          active(children, health.copy(
            contact      = health.contact.copy(updatedAt = now),
            message      = health.message.copy(updatedAt = now),
            publication  = health.publication.copy(updatedAt = now),
            gamification = health.gamification.copy(updatedAt = now)
          ), lookup)
      }
    }

  private def updateChild(h: Health, name: String)(f: ChildHealth => ChildHealth): Health = name match {
    case "contact"      => h.copy(contact = f(h.contact))
    case "message"      => h.copy(message = f(h.message))
    case "publication"  => h.copy(publication = f(h.publication))
    case "gamification" => h.copy(gamification = f(h.gamification))
    case _              => h
  }

  /** Fast-fail responde inmediatamente al replyTo del SubmitContact. */
  private def failContact(ctx: ActorContext[_], cmd: ContactCommand): Unit = cmd match {
    case SubmitContact(_, replyTo) =>
      ctx.log.warn("[DomainGuardian] fast-fail contact — child is Dead")
      replyTo ! ContactError("Servicio temporalmente no disponible")
    case _ =>
      ctx.log.warn(s"[DomainGuardian] DROP contact cmd — child is Dead")
  }
}
