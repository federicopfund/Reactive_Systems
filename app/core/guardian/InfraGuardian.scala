package core.guardian

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.util.Timeout
import core._
import services.AgentSettingsLookup

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * InfraGuardian — Capa 3 (Issue #15).
 *
 * Supervisa los actores de infraestructura: EventBus + Pipeline.
 * Como el `PublicationPipelineEngine` necesita las refs de los engines de las
 * otras 2 capas, en `setup`:
 *   1. Spawnea EventBusEngine inmediatamente.
 *   2. Pide al `DomainGuardian` y al `CrossCutGuardian` sus refs de hijos
 *      (vía `ctx.ask`), stasheando cualquier mensaje que llegue mientras tanto.
 *   3. Cuando recibe ambas respuestas, spawnea el Pipeline con los 6 refs y
 *      pasa a `active`, vaciando el stash.
 */

sealed trait InfraGuardianCommand
final case class ForwardEventBus(cmd: EventBusCommand)             extends InfraGuardianCommand
final case class ForwardPipeline(cmd: PipelineCommand)             extends InfraGuardianCommand
final case class GetInfraHealth(replyTo: ActorRef[GuardianHealth]) extends InfraGuardianCommand
private final case class WireDomainRefs(refs: DomainRefs)          extends InfraGuardianCommand
private final case class WireCrossCutRefs(refs: CrossCutRefs)      extends InfraGuardianCommand
private final case class WireFailure(reason: String)               extends InfraGuardianCommand
private final case class InfraChildTerminated(name: String)        extends InfraGuardianCommand
private case object InfraPingTick                                  extends InfraGuardianCommand

object InfraGuardian {

  private case class Children(
    eventBus: ActorRef[EventBusCommand],
    pipeline: ActorRef[PipelineCommand]
  )

  private case class Health(
    eventBus: ChildHealth,
    pipeline: ChildHealth
  ) {
    def asMap: Map[String, ChildHealth] = Map("eventbus" -> eventBus, "pipeline" -> pipeline)
    def isHealthy: Boolean = asMap.values.forall(_.status == ChildStatus.Healthy)
  }

  def apply(
    domainGuardian:   ActorRef[DomainGuardianCommand],
    crossCutGuardian: ActorRef[CrossCutGuardianCommand],
    lookup:           AgentSettingsLookup = AgentSettingsLookup.Defaults
  )(implicit ec: ExecutionContext): Behavior[InfraGuardianCommand] =
    Behaviors.withStash(capacity = 1000) { stash =>
      Behaviors.setup { ctx =>
        ctx.log.info("[InfraGuardian] Starting — spawning EventBus, then wiring pipeline")

        val maxRestarts       = lookup.getInt("supervision.infra.maxRestarts")
        val resetBackoffAfter = lookup.getDuration("supervision.infra.resetBackoffSec")
        val minBackoff        = lookup.getDuration("backoff.infra.minSec")
        val maxBackoff        = lookup.getDuration("backoff.infra.maxSec")
        val heartbeatInterval = lookup.getDuration("heartbeat.infra.intervalSec")

        def supervise[T](b: Behavior[T]): Behavior[T] =
          Behaviors
            .supervise(b)
            .onFailure[Throwable](
              SupervisorStrategy
                .restartWithBackoff(minBackoff, maxBackoff, randomFactor = 0.2)
                .withMaxRestarts(maxRestarts)
                .withResetBackoffAfter(resetBackoffAfter)
            )

        // 1) EventBus arranca solo (carga el config eventbus-cluster vía Module)
        val eventBus = ctx.spawn(supervise(EventBusEngine()), "eventbus")
        ctx.watchWith(eventBus, InfraChildTerminated("eventbus"))

        // 2) Pedir refs a los otros 2 guardians
        implicit val timeout: Timeout = 5.seconds
        ctx.ask[GetDomainRefs, DomainRefs](domainGuardian, ref => GetDomainRefs(ref)) {
          case Success(refs) => WireDomainRefs(refs)
          case Failure(ex)   => WireFailure(s"domain refs: ${ex.getMessage}")
        }
        ctx.ask[GetCrossCutRefs, CrossCutRefs](crossCutGuardian, ref => GetCrossCutRefs(ref)) {
          case Success(refs) => WireCrossCutRefs(refs)
          case Failure(ex)   => WireFailure(s"cross-cut refs: ${ex.getMessage}")
        }

        wiring(stash, ctx, supervise, eventBus, None, None, lookup, heartbeatInterval)
      }
    }

  private def wiring(
    stash:     StashBuffer[InfraGuardianCommand],
    ctx:       akka.actor.typed.scaladsl.ActorContext[InfraGuardianCommand],
    supervise: Behavior[PipelineCommand] => Behavior[PipelineCommand],
    eventBus:  ActorRef[EventBusCommand],
    domain:    Option[DomainRefs],
    crossCut:  Option[CrossCutRefs],
    lookup:    AgentSettingsLookup,
    heartbeat: FiniteDuration
  )(implicit ec: ExecutionContext): Behavior[InfraGuardianCommand] =
    Behaviors.receiveMessage {
      case WireDomainRefs(refs) =>
        tryWire(stash, ctx, supervise, eventBus, Some(refs), crossCut, lookup, heartbeat)
      case WireCrossCutRefs(refs) =>
        tryWire(stash, ctx, supervise, eventBus, domain, Some(refs), lookup, heartbeat)
      case WireFailure(reason) =>
        ctx.log.error(s"[InfraGuardian] wiring failed: $reason — guardian will keep retrying via stash")
        Behaviors.same
      case other =>
        stash.stash(other)
        Behaviors.same
    }

  private def tryWire(
    stash:     StashBuffer[InfraGuardianCommand],
    ctx:       akka.actor.typed.scaladsl.ActorContext[InfraGuardianCommand],
    supervise: Behavior[PipelineCommand] => Behavior[PipelineCommand],
    eventBus:  ActorRef[EventBusCommand],
    domain:    Option[DomainRefs],
    crossCut:  Option[CrossCutRefs],
    lookup:    AgentSettingsLookup,
    heartbeat: FiniteDuration
  )(implicit ec: ExecutionContext): Behavior[InfraGuardianCommand] =
    (domain, crossCut) match {
      case (Some(d), Some(cc)) =>
        ctx.log.info("[InfraGuardian] all refs received — spawning Pipeline")
        val pipeline = ctx.spawn(
          supervise(PublicationPipelineEngine(
            moderationEngine   = cc.moderation,
            publicationEngine  = d.publication,
            notificationEngine = cc.notification,
            gamificationEngine = d.gamification,
            analyticsEngine    = cc.analytics,
            eventBus           = eventBus
          )),
          "pipeline"
        )
        ctx.watchWith(pipeline, InfraChildTerminated("pipeline"))
        Behaviors.withTimers[InfraGuardianCommand] { timers =>
          timers.startTimerAtFixedRate(InfraPingTick, heartbeat)
          stash.unstashAll(active(Children(eventBus, pipeline), initialHealth(), lookup))
        }
      case _ =>
        wiring(stash, ctx, supervise, eventBus, domain, crossCut, lookup, heartbeat)
    }

  private def initialHealth(): Health = {
    val now = Instant.now()
    def fresh(name: String) = ChildHealth(name, ChildStatus.Healthy, 0, None, -1, now)
    Health(fresh("eventbus"), fresh("pipeline"))
  }

  private def active(children: Children, health: Health, lookup: AgentSettingsLookup): Behavior[InfraGuardianCommand] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case ForwardEventBus(cmd) =>
          if (!lookup.getBool("engines.eventbus.enabled")) {
            ctx.log.warn("[InfraGuardian] DROP eventbus — kill-switch off")
          } else if (health.eventBus.status != ChildStatus.Dead) children.eventBus ! cmd
          else ctx.log.warn("[InfraGuardian] DROP eventbus cmd — child is Dead")
          Behaviors.same

        case ForwardPipeline(cmd) =>
          if (!lookup.getBool("engines.pipeline.enabled")) {
            ctx.log.warn("[InfraGuardian] DROP pipeline — kill-switch off")
            cmd match {
              case ProcessNewPublication(_, _, _, _, _, _, _, _, _, replyTo) =>
                replyTo ! PipelineError("Pipeline deshabilitado por configuración", "guardian", "kill_switch")
              case _ =>
            }
          } else if (health.pipeline.status != ChildStatus.Dead) children.pipeline ! cmd
          else cmd match {
            case ProcessNewPublication(_, _, _, _, _, _, _, _, _, replyTo) =>
              ctx.log.warn("[InfraGuardian] fast-fail pipeline — child is Dead")
              replyTo ! PipelineError("Pipeline temporalmente no disponible", "guardian", "n/a")
            case _ => ctx.log.warn("[InfraGuardian] DROP pipeline cmd — child is Dead")
          }
          Behaviors.same

        case GetInfraHealth(replyTo) =>
          replyTo ! GuardianHealth("infrastructure", health.isHealthy, health.asMap)
          Behaviors.same

        case InfraChildTerminated(name) =>
          ctx.log.warn(s"[InfraGuardian] child '$name' terminated permanently")
          val updated = updateChild(health, name) { c =>
            val restarts = c.restarts + 1
            c.copy(
              status    = if (restarts >= 5) ChildStatus.Dead else ChildStatus.Degraded(restarts, "child terminated"),
              restarts  = restarts,
              lastError = Some("child terminated"),
              updatedAt = Instant.now()
            )
          }
          active(children, updated, lookup)

        case InfraPingTick =>
          val now = Instant.now()
          active(children, health.copy(
            eventBus = health.eventBus.copy(updatedAt = now),
            pipeline = health.pipeline.copy(updatedAt = now)
          ), lookup)

        case _: WireDomainRefs | _: WireCrossCutRefs | _: WireFailure =>
          // late wiring messages — ignore in active state
          Behaviors.same
      }
    }

  private def updateChild(h: Health, name: String)(f: ChildHealth => ChildHealth): Health = name match {
    case "eventbus" => h.copy(eventBus = f(h.eventBus))
    case "pipeline" => h.copy(pipeline = f(h.pipeline))
    case _          => h
  }
}
