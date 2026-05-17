package domains.publications.engines.publication

/**
 * Protocolo de respuestas del PublicationEngine.
 *
 * Usado por adapters y el pipeline para reaccionar al resultado
 * de cada operación sobre el ciclo de vida de una publicación.
 */
sealed trait PublicationResponse

case class PublicationCreatedOk(publicationId: Long) extends PublicationResponse
case class PublicationApproved(publicationId: Long)  extends PublicationResponse
case class PublicationRejected(publicationId: Long)  extends PublicationResponse
case class PublicationError(reason: String)          extends PublicationResponse
