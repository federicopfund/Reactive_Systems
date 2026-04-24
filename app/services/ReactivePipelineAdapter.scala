package services

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import core._
import core.guardian._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ReactivePipelineAdapter(
  guardian: ActorRef[InfraGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = Timeout(30.seconds)

  def processPublication(
    userId: Long, username: String, userEmail: Option[String],
    title: String, content: String, excerpt: Option[String],
    coverImage: Option[String], category: String, tags: Option[String]
  ): Future[PipelineResponse] =
    guardian.ask[PipelineResponse](ref => ForwardPipeline(ProcessNewPublication(
      userId, username, userEmail, title, content, excerpt, coverImage, category, tags, ref
    )))

  def getMetrics(): Future[PipelineMetricsSnapshot] =
    guardian.ask[PipelineResponse](ref => ForwardPipeline(GetPipelineMetrics(ref))).map {
      case m: PipelineMetricsSnapshot => m
      case other => throw new IllegalStateException(s"Unexpected pipeline response: $other")
    }
}
