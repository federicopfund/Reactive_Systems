package models

import java.time.Instant

/**
 * Tipos de feedback categorizado del admin/editor al autor.
 * Se conservan las cinco categorías existentes por compatibilidad
 * con datos ya creados antes de Sprint 1.
 */
object FeedbackType extends Enumeration {
  type FeedbackType = Value
  val ContentQuality = Value("content_quality")
  val Structure      = Value("structure")
  val Relevance      = Value("relevance")
  val WritingStyle   = Value("writing_style")
  val General        = Value("general")

  /** Etiqueta legible para la vista */
  def label(ft: String): String = ft match {
    case "content_quality" => "Calidad de Contenido"
    case "structure"       => "Estructura y Formato"
    case "relevance"       => "Relevancia Temática"
    case "writing_style"   => "Redacción y Estilo"
    case "general"         => "Sugerencia General"
    case other             => other
  }

  /** Icono para la vista */
  def icon(ft: String): String = ft match {
    case "content_quality" => "\uD83D\uDCCA"
    case "structure"       => "\uD83D\uDCC1"
    case "relevance"       => "\uD83C\uDFAF"
    case "writing_style"   => "\u270D\uFE0F"
    case "general"         => "\uD83D\uDCA1"
    case _                 => "\uD83D\uDCDD"
  }
}

/**
 * Visibilidad de un comentario editorial.
 *
 * - Both: visible para el autor y los editores (feedback regular que
 *         se le muestra al autor).
 * - EditorOnly: solo visible para editores (notas internas del equipo).
 *
 * Se deriva/sincroniza con el campo legado `sentToUser`:
 *   sentToUser = true  ↔ visibility = Both
 *   sentToUser = false ↔ visibility = EditorOnly
 *
 * Esta redundancia se mantiene durante la transición. A partir del
 * Sprint 2, visibility es la fuente de verdad y sentToUser se ignora.
 */
object FeedbackVisibility {
  val Both       = "both"
  val EditorOnly = "editor_only"

  val all: Set[String] = Set(Both, EditorOnly)

  /** Traducción desde el flag legado */
  def fromSentToUser(sent: Boolean): String =
    if (sent) Both else EditorOnly

  /** Traducción al flag legado */
  def toSentToUser(visibility: String): Boolean =
    visibility == Both
}

/**
 * Feedback editorial sobre una publicación.
 *
 * ── Nombre físico vs conceptual ──
 *
 * La tabla se llama publication_feedback (preservado desde evolution 8).
 * El campo adminId se preserva con ese nombre, aunque conceptualmente
 * representa "el editor que comentó", sea admin del sistema o editor
 * del equipo editorial. Los Actions de Sprint 2+ asegurarán que solo
 * usuarios con rol editorial puedan escribir aquí.
 *
 * ── Campos nuevos del Sprint 1 ──
 *
 *   revisionId     : versión del texto sobre la que aplica el comentario.
 *                    None = comentario general.
 *
 *   parentId       : comentario padre para hilos. None = raíz.
 *
 *   anchorSelector : selector opaco para anclar al texto. El formato lo
 *                    decide el editor frontend. None = no anclado.
 *
 *   anchorText     : texto citado al momento del comentario, preservado
 *                    aunque el texto cambie.
 *
 *   resolvedAt     : cuándo se resolvió. None = pendiente.
 *
 *   resolvedBy     : quién resolvió (autor marcando "incorporé" o editor
 *                    cerrando).
 *
 *   visibility     : "both" o "editor_only".
 */
case class PublicationFeedback(
  id: Option[Long] = None,
  publicationId: Long,
  adminId: Long,
  feedbackType: String,
  message: String,
  sentToUser: Boolean = false,
  createdAt: Instant = Instant.now(),
  // ── Campos editoriales (Sprint 1) ──
  revisionId: Option[Long] = None,
  parentId: Option[Long] = None,
  anchorSelector: Option[String] = None,
  anchorText: Option[String] = None,
  resolvedAt: Option[Instant] = None,
  resolvedBy: Option[Long] = None,
  visibility: String = FeedbackVisibility.Both
) {
  /** True si el comentario no fue resuelto aún. */
  def isPending: Boolean = resolvedAt.isEmpty

  /** True si es un comentario anclado a un fragmento del texto. */
  def isAnchored: Boolean = anchorSelector.isDefined

  /** True si es un comentario raíz (no respuesta en un hilo). */
  def isRoot: Boolean = parentId.isEmpty

  /** True si el autor puede verlo. */
  def isVisibleToAuthor: Boolean = visibility == FeedbackVisibility.Both
}

/**
 * Vista enriquecida con nombre del autor del comentario.
 * Usada por las vistas de detalle de publicación.
 */
case class PublicationFeedbackWithAdmin(
  feedback: PublicationFeedback,
  adminUsername: String
)
