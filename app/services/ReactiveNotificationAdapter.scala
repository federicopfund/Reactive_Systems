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
class ReactiveNotificationAdapter @Inject()(
  guardian: ActorRef[CrossCutGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 5.seconds

  def notify(
    userId: Long, userEmail: Option[String], notificationType: String,
    title: String, message: String,
    publicationId: Option[Long] = None, channels: Set[String] = Set("inapp")
  ): Unit =
    guardian ! ForwardNotification(SendNotification(userId, userEmail, notificationType, title, message, publicationId, channels, None))

  def notifyAsync(
    userId: Long, userEmail: Option[String], notificationType: String,
    title: String, message: String,
    publicationId: Option[Long] = None, channels: Set[String] = Set("inapp")
  ): Future[NotificationResponse] =
    guardian.ask[NotificationResponse] { replyTo =>
      ForwardNotification(SendNotification(userId, userEmail, notificationType, title, message, publicationId, channels, Some(replyTo)))
    }

  def notifyBulk(
    userIds: Seq[Long], notificationType: String, title: String, message: String,
    publicationId: Option[Long] = None
  ): Unit =
    guardian ! ForwardNotification(SendBulkNotification(userIds, notificationType, title, message, publicationId))
}
