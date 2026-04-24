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
class ReactivePublicationAdapter @Inject()(
  guardian: ActorRef[DomainGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 10.seconds

  def createPublication(
    userId: Long, username: String, title: String, content: String,
    excerpt: Option[String], coverImage: Option[String], category: String, tags: Option[String]
  ): Future[PublicationResponse] =
    guardian.ask[PublicationResponse] { replyTo =>
      ForwardPublication(CreatePublication(userId, username, title, content, excerpt, coverImage, category, tags, replyTo))
    }

  def approvePublication(publicationId: Long, adminId: Long, adminUsername: String): Future[PublicationResponse] =
    guardian.ask[PublicationResponse] { replyTo =>
      ForwardPublication(ApprovePublication(publicationId, adminId, adminUsername, replyTo))
    }

  def rejectPublication(publicationId: Long, adminId: Long, adminUsername: String, reason: String): Future[PublicationResponse] =
    guardian.ask[PublicationResponse] { replyTo =>
      ForwardPublication(RejectPublication(publicationId, adminId, adminUsername, reason, replyTo))
    }
}
