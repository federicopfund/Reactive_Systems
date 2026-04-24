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
class ReactiveModerationAdapter @Inject()(
  guardian: ActorRef[CrossCutGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 5.seconds

  def moderate(
    contentId: Long, contentType: String, authorId: Long,
    title: Option[String], content: String
  ): Future[ModerationResponse] =
    guardian.ask[ModerationResponse] { replyTo =>
      ForwardModeration(ModerateContent(contentId, contentType, authorId, title, content, replyTo))
    }

  def updateBlocklist(words: Set[String]): Unit =
    guardian ! ForwardModeration(UpdateBlocklist(words))
}
