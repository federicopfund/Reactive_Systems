package domains.gamification.engines.gamification

/**
 * Protocolo de respuestas del GamificationEngine.
 *
 *   - [[BadgesAwarded]]      → lista de badge keys otorgados (puede ser vacía).
 *   - [[GamificationError]]  → fallo en la verificación.
 */
sealed trait GamificationResponse
case class BadgesAwarded(badges: List[String]) extends GamificationResponse
case class GamificationError(reason: String) extends GamificationResponse
