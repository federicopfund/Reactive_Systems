package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import repositories.BadgeRepository
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

/**
 * GamificationEngine — Actor reactivo para el sistema de badges y logros.
 *
 * Convierte el servicio síncrono GamificationService en un actor
 * que procesa verificaciones de badges de forma fire-and-forget,
 * desacoplando la lógica de gamificación del flujo principal.
 *
 * Principios Reactivos:
 *   - Message-Driven: cada check es un mensaje independiente
 *   - Elastic: puede acumular checks sin bloquear el caller
 *   - Resilient: fallos en badges no afectan la operación principal
 */

// ── Commands ──
sealed trait GamificationCommand

case class CheckBadges(
  userId: Long,
  triggerType: String, // "publication", "comment", "reaction", "login"
  metadata: Map[String, Long],
  replyTo: Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

case class AwardBadge(
  userId: Long,
  badgeKey: String,
  replyTo: Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

private case class BadgesChecked(
  userId: Long,
  awarded: List[String],
  replyTo: Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

private case class BadgeCheckFailed(
  userId: Long,
  exception: Throwable,
  replyTo: Option[ActorRef[GamificationResponse]]
) extends GamificationCommand

private case class BadgeAwarded(userId: Long, badgeKey: String) extends GamificationCommand
private case class BadgeAwardFailed(exception: Throwable, userId: Long, badgeKey: String) extends GamificationCommand

// ── Responses ──
sealed trait GamificationResponse
case class BadgesAwarded(badges: List[String]) extends GamificationResponse
case class GamificationError(reason: String) extends GamificationResponse

object GamificationEngine {

  def apply(
    badgeRepo: BadgeRepository
  )(implicit ec: ExecutionContext): Behavior[GamificationCommand] =
    active(badgeRepo)

  private def active(
    badgeRepo: BadgeRepository
  )(implicit ec: ExecutionContext): Behavior[GamificationCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        case CheckBadges(userId, triggerType, metadata, replyTo) =>
          context.log.info(s"[GamificationEngine] Checking badges for user $userId (trigger: $triggerType)")

          val checks: Seq[(String, Boolean)] = triggerType match {
            case "publication" =>
              val count = metadata.getOrElse("publicationCount", 0L)
              val approved = metadata.getOrElse("approvedCount", 0L)
              val views = metadata.getOrElse("totalViews", 0L)
              Seq(
                ("first_publication", count >= 1),
                ("five_publications", count >= 5),
                ("ten_publications", count >= 10),
                ("first_approved", approved >= 1),
                ("hundred_views", views >= 100),
                ("five_hundred_views", views >= 500)
              )
            case "comment" =>
              val count = metadata.getOrElse("commentCount", 0L)
              Seq(
                ("first_comment", count >= 1),
                ("ten_comments", count >= 10)
              )
            case "reaction" =>
              val total = metadata.getOrElse("totalReactions", 0L)
              Seq(
                ("ten_likes", total >= 10),
                ("fifty_likes", total >= 50)
              )
            case _ => Seq.empty
          }

          val eligible = checks.filter(_._2).map(_._1)

          import scala.concurrent.Future
          val awardFuture = Future.sequence(eligible.map { key =>
            badgeRepo.award(userId, key).map(awarded => if (awarded) Some(key) else None)
          }).map(_.flatten.toList)

          context.pipeToSelf(awardFuture) {
            case Success(awarded) => BadgesChecked(userId, awarded, replyTo)
            case Failure(ex)      => BadgeCheckFailed(userId, ex, replyTo)
          }
          Behaviors.same

        case AwardBadge(userId, badgeKey, replyTo) =>
          context.log.info(s"[GamificationEngine] Awarding badge '$badgeKey' to user $userId")
          context.pipeToSelf(badgeRepo.award(userId, badgeKey)) {
            case Success(true)  => BadgeAwarded(userId, badgeKey)
            case Success(false) => BadgeAwarded(userId, badgeKey) // already had it
            case Failure(ex)    => BadgeAwardFailed(ex, userId, badgeKey)
          }
          replyTo.foreach(_ ! BadgesAwarded(List(badgeKey)))
          Behaviors.same

        case BadgesChecked(userId, awarded, replyTo) =>
          if (awarded.nonEmpty) {
            context.log.info(s"[GamificationEngine] User $userId earned badges: ${awarded.mkString(", ")}")
          }
          replyTo.foreach(_ ! BadgesAwarded(awarded))
          Behaviors.same

        case BadgeCheckFailed(userId, ex, replyTo) =>
          context.log.error(s"[GamificationEngine] Badge check failed for user $userId: ${ex.getMessage}")
          replyTo.foreach(_ ! GamificationError(ex.getMessage))
          Behaviors.same

        case BadgeAwarded(userId, badgeKey) =>
          context.log.info(s"[GamificationEngine] Badge '$badgeKey' awarded to user $userId")
          Behaviors.same

        case BadgeAwardFailed(ex, userId, badgeKey) =>
          context.log.error(s"[GamificationEngine] Failed to award '$badgeKey' to user $userId: ${ex.getMessage}")
          Behaviors.same
      }
    }
  }
}
