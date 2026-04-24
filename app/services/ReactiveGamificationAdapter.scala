package services

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import core._
import core.guardian._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class ReactiveGamificationAdapter @Inject()(
  guardian: ActorRef[DomainGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 5.seconds

  def checkBadges(userId: Long, triggerType: String, metadata: Map[String, Long]): Unit =
    guardian ! ForwardGamification(CheckBadges(userId, triggerType, metadata, None))

  def checkBadgesAsync(userId: Long, triggerType: String, metadata: Map[String, Long]): Future[GamificationResponse] =
    guardian.ask[GamificationResponse] { replyTo =>
      ForwardGamification(CheckBadges(userId, triggerType, metadata, Some(replyTo)))
    }

  def awardBadge(userId: Long, badgeKey: String): Unit =
    guardian ! ForwardGamification(AwardBadge(userId, badgeKey, None))
}
