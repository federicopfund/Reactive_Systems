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
class ReactiveContactAdapter @Inject()(
  guardian: ActorRef[DomainGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 5.seconds
  def submitContact(contact: Contact): Future[ContactResponse] =
    guardian.ask[ContactResponse](replyTo => ForwardContact(SubmitContact(contact, replyTo)))
}
