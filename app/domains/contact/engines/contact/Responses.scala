package domains.contact.engines.contact

/**
 * Protocolo de respuestas del ContactEngine.
 *
 *   - [[ContactSubmitted]] → formulario guardado exitosamente.
 *   - [[ContactError]]     → fallo en persistencia.
 */
sealed trait ContactResponse
case class ContactSubmitted(id: String) extends ContactResponse
case class ContactError(message: String) extends ContactResponse
