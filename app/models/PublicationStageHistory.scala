package models

import java.time.Instant

/**
 * Registro histórico de una transición de etapa de una publicación.
 *
 * La invariante del sistema: para cada publicationId, exactamente una fila
 * tiene exitedAt = None. Esa es la etapa actual.
 *
 * La invariante se mantiene por trigger de base de datos (ver evolution 14).
 *
 * @param id             PK
 * @param publicationId  Publicación afectada
 * @param stageId        Etapa a la que entró la pieza
 * @param enteredAt      Cuándo entró a esta etapa
 * @param exitedAt       Cuándo salió (None = etapa actual)
 * @param enteredBy      Usuario que provocó la transición (None = sistema o retroactivo)
 * @param reason         Motivo (obligatorio en vueltas atrás, opcional en avances)
 * @param internalNotes  Notas no visibles para el autor
 */
case class PublicationStageHistory(
  id: Option[Long] = None,
  publicationId: Long,
  stageId: Long,
  enteredAt: Instant = Instant.now(),
  exitedAt: Option[Instant] = None,
  enteredBy: Option[Long] = None,
  reason: Option[String] = None,
  internalNotes: Option[String] = None
) {
  /** True si esta fila representa la etapa actual de la publicación. */
  def isCurrent: Boolean = exitedAt.isEmpty

  /** Duración en la etapa si ya se salió. */
  def duration: Option[java.time.Duration] =
    exitedAt.map(out => java.time.Duration.between(enteredAt, out))
}

/**
 * Vista enriquecida con datos denormalizados de la etapa.
 * Útil para no hacer JOIN en cada render.
 */
case class PublicationStageHistoryWithStage(
  history: PublicationStageHistory,
  stageCode: String,
  stageLabel: String,
  enteredByUsername: Option[String]
)
