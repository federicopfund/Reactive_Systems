package domains.messaging.services

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import domains.messaging.engines._
import infrastructure.guardian._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class ReactiveMessageAdapter @Inject()(
  guardian: ActorRef[DomainGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 5.seconds

  def sendMessage(
    senderId: Long,
    senderUsername: String,
    receiverId: Long,
    publicationId: Option[Long],
    publicationTitle: Option[String],
    subject: String,
    content: String
  ): Future[MessageResponse] =
    guardian.ask[MessageResponse] { replyTo =>
      ForwardMessage(SendPrivateMessage(senderId, senderUsername, receiverId, publicationId, publicationTitle, subject, content, replyTo))
    }
}
