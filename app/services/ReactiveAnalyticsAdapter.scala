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
class ReactiveAnalyticsAdapter @Inject()(
  guardian: ActorRef[CrossCutGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = 3.seconds

  def trackEvent(eventType: String, userId: Option[Long] = None, metadata: Map[String, String] = Map.empty): Unit =
    guardian ! ForwardAnalytics(TrackEvent(eventType, userId, metadata))

  def trackPageView(path: String, userId: Option[Long] = None, referrer: Option[String] = None): Unit =
    guardian ! ForwardAnalytics(TrackPageView(path, userId, referrer))

  def trackPublicationView(publicationId: Long, userId: Option[Long] = None): Unit =
    guardian ! ForwardAnalytics(TrackPublicationView(publicationId, userId))

  def getMetrics: Future[AnalyticsResponse] =
    guardian.ask[AnalyticsResponse](replyTo => ForwardAnalytics(GetMetrics(replyTo)))

  def resetMetrics(): Unit =
    guardian ! ForwardAnalytics(ResetMetrics)
}
