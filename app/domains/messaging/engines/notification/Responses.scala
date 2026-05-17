package domains.messaging.engines.notification

/**
 * Protocolo de respuestas del NotificationEngine.
 *
 *   - [[NotificationQueued]]       → canales encolados para entrega.
 *   - [[NotificationDeliveryError]] → fallo en al menos un canal.
 *   - [[CircuitBreakerStatus]]     → snapshot del Circuit Breaker del canal email.
 */
sealed trait NotificationResponse
case class NotificationQueued(channels: Set[String]) extends NotificationResponse
case class NotificationDeliveryError(reason: String) extends NotificationResponse
case class CircuitBreakerStatus(
  state:               String,
  consecutiveFailures: Int,
  totalTripped:        Int,
  emailsSkipped:       Long
) extends NotificationResponse
