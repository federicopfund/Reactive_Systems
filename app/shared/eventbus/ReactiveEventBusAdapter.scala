package shared.eventbus

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import shared.eventbus._
import shared.DomainEvent
import infrastructure.guardian._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ReactiveEventBusAdapter(
  guardian: ActorRef[InfraGuardianCommand],
  implicit val scheduler: Scheduler
)(implicit ec: ExecutionContext) {
  private implicit val timeout: Timeout = Timeout(3.seconds)

  def publish(event: DomainEvent): Unit =
    guardian ! ForwardEventBus(PublishEvent(event))

  def getMetrics(): Future[EventBusMetrics] =
    guardian.ask[EventBusResponse](ref => ForwardEventBus(GetEventBusMetrics(ref))).map {
      case m: EventBusMetrics => m
    }
}
