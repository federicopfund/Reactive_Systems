package core

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import java.time.Instant

/**
 * Domain Events — Vocabulario compartido entre agentes reactivos.
 *
 * Los eventos de dominio representan hechos inmutables que ya ocurrieron.
 * Cada evento lleva suficiente contexto para que cualquier suscriptor
 * reaccione sin necesidad de consultar otros servicios.
 *
 * Características:
 *   - correlationId: trazabilidad end-to-end a través de todos los agentes
 *   - timestamp: orden causal de los eventos
 *   - eventType: topic para filtrado en el EventBus (Pub/Sub)
 *
 * Patrón: Event-Driven Architecture
 * Principio Reactivo: Message-Driven con desacoplamiento total
 */
// Issue #14 — anotaciones para que Jackson pueda (de)serializar la jerarquía
// cuando los eventos viajen entre nodos del cluster vía DistributedPubSub.
// En modo 1-nodo no se ejercita, pero queda lista para multi-nodo.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[PublicationSubmittedEvent],     name = "publication.submitted"),
  new JsonSubTypes.Type(value = classOf[PublicationApprovedEvent],      name = "publication.approved"),
  new JsonSubTypes.Type(value = classOf[PublicationRejectedEvent],      name = "publication.rejected"),
  new JsonSubTypes.Type(value = classOf[ContentModeratedEvent],         name = "content.moderated"),
  new JsonSubTypes.Type(value = classOf[BadgeEarnedEvent],              name = "badge.earned"),
  new JsonSubTypes.Type(value = classOf[UserActionEvent],               name = "user.action"),
  new JsonSubTypes.Type(value = classOf[NotificationDeliveredEvent],    name = "notification.delivered"),
  new JsonSubTypes.Type(value = classOf[CircuitBreakerStateChangedEvent], name = "system.circuit_breaker"),
  new JsonSubTypes.Type(value = classOf[PipelineCompletedEvent],        name = "pipeline.completed")
))
sealed trait DomainEvent {
  def eventType: String
  def timestamp: Instant
  def correlationId: String
}

// ── Publication Events ──

case class PublicationSubmittedEvent(
  publicationId: Long,
  userId: Long,
  username: String,
  title: String,
  content: String,
  category: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "publication.submitted"
}

case class PublicationApprovedEvent(
  publicationId: Long,
  userId: Long,
  adminUsername: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "publication.approved"
}

case class PublicationRejectedEvent(
  publicationId: Long,
  userId: Long,
  adminUsername: String,
  reason: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "publication.rejected"
}

// ── Moderation Events ──

case class ContentModeratedEvent(
  contentId: Long,
  contentType: String,
  verdict: String,
  score: Double,
  flags: List[String],
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "content.moderated"
}

// ── Gamification Events ──

case class BadgeEarnedEvent(
  userId: Long,
  badgeKey: String,
  triggerType: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "badge.earned"
}

// ── User Events ──

case class UserActionEvent(
  userId: Long,
  action: String,
  metadata: Map[String, String],
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "user.action"
}

// ── Notification Events ──

case class NotificationDeliveredEvent(
  userId: Long,
  channel: String,
  notificationType: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "notification.delivered"
}

// ── System Events ──

case class CircuitBreakerStateChangedEvent(
  service: String,
  oldState: String,
  newState: String,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "system.circuit_breaker"
}

case class PipelineCompletedEvent(
  publicationId: Long,
  userId: Long,
  verdict: String,
  processingTimeMs: Long,
  correlationId: String = java.util.UUID.randomUUID().toString.take(8),
  timestamp: Instant = Instant.now()
) extends DomainEvent {
  val eventType = "pipeline.completed"
}
