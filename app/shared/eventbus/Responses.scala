package shared.eventbus

import java.time.Instant

/**
 * Protocolo de respuestas del EventBusEngine.
 *
 *   - [[EventBusMetrics]] → throughput y estado del bus (responde a [[GetEventBusMetrics]]).
 */
sealed trait EventBusResponse

case class EventBusMetrics(
  totalEventsPublished: Long,
  subscriberCount:      Int,
  deadLetterCount:      Long,
  recentEvents:         List[String],
  since:                Instant
) extends EventBusResponse
