package models

import java.time.Instant

/**
 * Estados legados de una publicación.
 *
 * Desde Sprint 1 el campo `status` se mantiene por compatibilidad pero
 * deja de ser la fuente de verdad. La fuente real es
 * `publication_stage_history`, cacheada en `Publication.currentStageId`.
 *
 * El mapeo entre ambos mundos vive en `EditorialStageCode.fromLegacyStatus`
 * y `EditorialStageCode.toLegacyStatus`. La sincronización automática entre
 * los dos llega en Sprint 2 vía trigger unidireccional.
 */
object PublicationStatus extends Enumeration {
  type PublicationStatus = Value
  val Draft    = Value("draft")
  val Pending  = Value("pending")
  val Approved = Value("approved")
  val Rejected = Value("rejected")
}

/**
 * Tipos de publicación.
 *
 * Determinan valores por defecto del flujo editorial:
 *   - Tutorial: requiere revisión técnica por defecto
 *   - Article, Column, Interview: no la requieren por defecto
 *
 * El valor `requires_technical_review` por pieza puede sobrescribir el
 * default del tipo (un editor lo puede cambiar manualmente).
 */
object PublicationType {
  val Article   = "article"
  val Tutorial  = "tutorial"
  val Column    = "column"
  val Interview = "interview"

  val all: Set[String] = Set(Article, Tutorial, Column, Interview)

  /** ¿Este tipo de pieza requiere revisión técnica por defecto? */
  def defaultRequiresTechnicalReview(tpe: String): Boolean = tpe match {
    case Tutorial => true
    case _        => false
  }

  /** Etiqueta visible para UI */
  def label(tpe: String): String = tpe match {
    case Article   => "Artículo"
    case Tutorial  => "Tutorial"
    case Column    => "Columna"
    case Interview => "Entrevista"
    case other     => other
  }
}

/**
 * Publicación editorial.
 *
 * ── Campos nuevos del Sprint 1 (editorial administrada) ──
 *
 *   currentStageId         : cache de la etapa actual. Derivado de
 *                            publication_stage_history. None solo para
 *                            piezas recién insertadas antes de que se
 *                            ejecute el backfill o el trigger de sync.
 *
 *   publicationType        : tipo de pieza. Default "article".
 *                            Valores: article | tutorial | column | interview.
 *
 *   requiresTechnicalReview: flag por pieza. Tutorials arrancan true;
 *                            otros false. Editable.
 *
 * ── Campos legados ──
 *
 * Los siguientes se mantienen por compatibilidad hasta el cutover final:
 *   - status: se sincroniza con currentStageId (Sprint 2+)
 *   - reviewedBy, reviewedAt: reemplazados por stage_history.entered_by
 *   - rejectionReason: reemplazado por editorial_feedback
 */
case class Publication(
  id: Option[Long] = None,
  userId: Long,
  title: String,
  slug: String,
  content: String,
  excerpt: Option[String] = None,
  coverImage: Option[String] = None,
  category: String,
  tags: Option[String] = None,
  status: String = PublicationStatus.Draft.toString,
  viewCount: Int = 0,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now(),
  publishedAt: Option[Instant] = None,
  reviewedBy: Option[Long] = None,
  reviewedAt: Option[Instant] = None,
  rejectionReason: Option[String] = None,
  adminNotes: Option[String] = None,
  // ── Campos editoriales (Sprint 1) ──
  currentStageId: Option[Long] = None,
  publicationType: String = PublicationType.Article,
  requiresTechnicalReview: Boolean = false
)

/**
 * Publication enriquecida con datos del autor (y opcionalmente del revisor).
 * Se construye vía JOIN en PublicationRepository.
 */
case class PublicationWithAuthor(
  publication: Publication,
  authorUsername: String,
  authorFullName: String,
  reviewerUsername: Option[String] = None
)
