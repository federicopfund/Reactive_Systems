package domains.gamification.engines.gamification

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del GamificationEngine.
 *
 * Dos niveles de visibilidad:
 *   - [[CheckBadges]] / [[AwardBadge]] → API pública (fire-and-forget con replyTo opcional).
 *   - Internos `private[engines]`      → resultados de los pipelines asíncronos.
 */
sealed trait GamificationCommand

// ── API pública ──────────────────────────────────────────────────────────────

case class CheckBadges(
  userId:      Long,
  triggerType: String, // "publication", "comment", "reaction", "login"
  metadata:    Map[String, Long],
  replyTo:     Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

case class AwardBadge(
  userId:   Long,
  badgeKey: String,
  replyTo:  Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

// ── Protocolo interno ────────────────────────────────────────────────────────

private[engines] case class BadgesChecked(
  userId:  Long,
  awarded: List[String],
  replyTo: Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

private[engines] case class BadgeCheckFailed(
  userId:    Long,
  exception: Throwable,
  replyTo:   Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

private[engines] case class BadgeAwarded(
  userId:   Long,
  badgeKey: String
) extends GamificationCommand

private[engines] case class BadgeAwardFailed(
  exception: Throwable,
  userId:    Long,
  badgeKey:  String
) extends GamificationCommand
