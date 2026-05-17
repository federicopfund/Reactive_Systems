package shared.moderation

/**
 * Protocolo de respuestas del ModerationEngine.
 *
 *   - [[ModerationResult]] → veredicto del pipeline de filtros automáticos.
 *     Verdict values: "auto_approved" | "pending_review" | "auto_rejected"
 */
sealed trait ModerationResponse

case class ModerationResult(
  contentId: Long,
  verdict:   String, // "auto_approved", "pending_review", "auto_rejected"
  flags:     List[String],
  score:     Double
) extends ModerationResponse
