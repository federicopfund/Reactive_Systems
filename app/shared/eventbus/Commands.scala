package shared.eventbus

import akka.actor.typed.ActorRef
import shared.DomainEvent

/**
 * Protocolo de comandos del EventBusEngine (Pub/Sub distribuido).
 *
 * Todos los comandos son públicos.
 * El routing real delega en DistributedPubSubMediator (Akka Cluster).
 */
sealed trait EventBusCommand

case class PublishEvent(event: DomainEvent) extends EventBusCommand

case class SubscribeToEvents(
  subscriber: ActorRef[DomainEvent],
  topics:     Set[String] // "publication", "content", "badge", "*" para todos
) extends EventBusCommand

case class UnsubscribeFromEvents(
  subscriber: ActorRef[DomainEvent]
) extends EventBusCommand

case class GetEventBusMetrics(
  replyTo: ActorRef[EventBusResponse]
) extends EventBusCommand
