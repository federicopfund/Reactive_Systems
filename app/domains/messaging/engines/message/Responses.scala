package domains.messaging.engines.message

/**
 * Protocolo de respuestas del MessageEngine (mensajería privada).
 *
 *   - [[MessageSent]]  → mensaje persistido exitosamente.
 *   - [[MessageError]] → fallo en persistencia.
 */
sealed trait MessageResponse
case class MessageSent(messageId: Long) extends MessageResponse
case class MessageError(reason: String) extends MessageResponse
