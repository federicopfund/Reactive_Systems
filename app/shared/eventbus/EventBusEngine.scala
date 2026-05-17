package shared.eventbus

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.{actor => classic}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import java.time.Instant
import shared.DomainEvent

/**
 * EventBusEngine — Bus de eventos de dominio (Pub/Sub distribuido).
 *
 * Issue #14 — Migrado a `akka.cluster.pubsub.DistributedPubSubMediator`.
 * El routing antes era O(n) sobre un `Map[ActorRef, Set[String]]`; ahora
 * delega en el Mediator del cluster, que mantiene una tabla de suscriptores
 * por topic con lookup O(1) y replicación gossip entre nodos.
 *
 * Ventajas frente a la implementación previa:
 *   - Routing O(1) por topic vía `Publish(topic, event)`.
 *   - Sin SPOF: el estado de suscriptores se replica entre los nodos.
 *   - Escalable: cualquier nuevo nodo del cluster `eventbus-core` recibe
 *     y entrega eventos sin cambios de código.
 *   - DeathWatch automático: el Mediator limpia suscriptores caídos vía
 *     cluster membership (`MemberUnreachable`).
 *
 * Topics:
 *   - El topic se deriva de la primera parte de `event.eventType` (antes del ".").
 *   - Se publica además al topic comodín `"*"` para suscriptores globales.
 *
 * Contrato preservado (los 9 agentes y el Pipeline siguen iguales):
 *   - `PublishEvent`, `SubscribeToEvents`, `UnsubscribeFromEvents`,
 *     `GetEventBusMetrics` mantienen su firma pública.
 */

object EventBusEngine {

  // Tabla mínima para soportar `UnsubscribeFromEvents` (que sólo trae el ref,
  // no los topics). El routing real lo hace el Mediator — esto es bookkeeping.
  private case class State(
    subscriptions: Map[ActorRef[DomainEvent], Set[String]] = Map.empty,
    totalPublished: Long = 0,
    deadLetters: Long = 0,
    recentEvents: List[String] = Nil,
    since: Instant = Instant.now()
  )

  def apply(): Behavior[EventBusCommand] =
    Behaviors.setup { context =>
      val mediator: classic.ActorRef =
        DistributedPubSub(context.system.toClassic).mediator
      context.log.info("[EventBus] DistributedPubSub Mediator ready — Cluster Pub/Sub active")
      active(mediator, State())
    }

  private def active(
    mediator: classic.ActorRef,
    state: State
  ): Behavior[EventBusCommand] = {
    Behaviors.receive { (context, message) =>
      message match {

        // ═══════════════════════════════════════
        // PUBLISH: Routing O(1) vía Mediator
        // ═══════════════════════════════════════
        case PublishEvent(event) =>
          val topic = event.eventType.split("\\.").headOption.getOrElse("*")
          context.log.info(
            s"[EventBus] Publishing ${event.eventType} [${event.correlationId}] → topic=$topic"
          )
          mediator ! DistributedPubSubMediator.Publish(topic, event, sendOneMessageToEachGroup = false)
          // Topic comodín para suscriptores globales (`Set("*")`).
          mediator ! DistributedPubSubMediator.Publish("*", event, sendOneMessageToEachGroup = false)
          active(mediator, state.copy(
            totalPublished = state.totalPublished + 1,
            recentEvents = (event.eventType :: state.recentEvents).take(50)
          ))

        // ═══════════════════════════════════════
        // SUBSCRIBE: delega en el Mediator (propagación cluster-wide)
        // ═══════════════════════════════════════
        case SubscribeToEvents(subscriber, topics) =>
          context.log.info(
            s"[EventBus] +subscriber ${subscriber.path.name} → topics: ${topics.mkString(", ")}"
          )
          val classicRef = subscriber.toClassic
          topics.foreach { topic =>
            mediator ! DistributedPubSubMediator.Subscribe(topic, classicRef)
          }
          val merged = state.subscriptions.getOrElse(subscriber, Set.empty) ++ topics
          active(mediator, state.copy(
            subscriptions = state.subscriptions + (subscriber -> merged)
          ))

        // ═══════════════════════════════════════
        // UNSUBSCRIBE: explícito por suscriptor
        // ═══════════════════════════════════════
        case UnsubscribeFromEvents(subscriber) =>
          context.log.info(s"[EventBus] -subscriber ${subscriber.path.name}")
          val classicRef = subscriber.toClassic
          state.subscriptions.getOrElse(subscriber, Set.empty).foreach { topic =>
            mediator ! DistributedPubSubMediator.Unsubscribe(topic, classicRef)
          }
          active(mediator, state.copy(subscriptions = state.subscriptions - subscriber))

        // ═══════════════════════════════════════
        // METRICS: estado observable del bus
        // ═══════════════════════════════════════
        case GetEventBusMetrics(replyTo) =>
          replyTo ! EventBusMetrics(
            totalEventsPublished = state.totalPublished,
            subscriberCount = state.subscriptions.size,
            deadLetterCount = state.deadLetters,
            recentEvents = state.recentEvents,
            since = state.since
          )
          Behaviors.same
      }
    }
  }
}
