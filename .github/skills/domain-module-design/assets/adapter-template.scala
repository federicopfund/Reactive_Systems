package domains.<dominio>.services

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import infrastructure.guardian.{DomainGuardianCommand, Forward<Nombre>}
import domains.<dominio>.engines._
import domains.<dominio>.models._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Reactive<Nombre>Adapter — fachada inyectable entre Play y el DomainGuardian.
 *
 * Contrato:
 *   - Expone `Future[_]`, nunca `ActorRef`.
 *   - El timeout de ask (5s) debe ser < timeout del request HTTP de Play (60s).
 *   - Los errores del engine (`<Nombre>Error`) se convierten en `Future.failed`.
 */
@Singleton
class Reactive<Nombre>Adapter @Inject()(
  domain:    ActorRef[DomainGuardianCommand],
  scheduler: Scheduler,
  ec:        ExecutionContext
) {
  implicit private val timeout: Timeout    = 5.seconds
  implicit private val s: Scheduler       = scheduler
  implicit private val e: ExecutionContext = ec

  def create(req: Create<Nombre>Request): Future[<Nombre>View] =
    domain.ask(ref => Forward<Nombre>(Create<Nombre>(req, ref))).flatMap {
      case <Nombre>Created(view)  => Future.successful(view)
      case <Nombre>Error(reason)  => Future.failed(new RuntimeException(reason))
      case other                  => Future.failed(new RuntimeException(s"Unexpected: $other"))
    }

  def get(id: Long): Future[Option[<Nombre>View]] =
    domain.ask(ref => Forward<Nombre>(Get<Nombre>(id, ref))).flatMap {
      case <Nombre>Found(view)    => Future.successful(Some(view))
      case <Nombre>NotFound(_)    => Future.successful(None)
      case <Nombre>Error(reason)  => Future.failed(new RuntimeException(reason))
      case other                  => Future.failed(new RuntimeException(s"Unexpected: $other"))
    }

  def update(id: Long, req: Update<Nombre>Request): Future[Option[<Nombre>View]] =
    domain.ask(ref => Forward<Nombre>(Update<Nombre>(id, req, ref))).flatMap {
      case <Nombre>Updated(view)  => Future.successful(Some(view))
      case <Nombre>NotFound(_)    => Future.successful(None)
      case <Nombre>Error(reason)  => Future.failed(new RuntimeException(reason))
      case other                  => Future.failed(new RuntimeException(s"Unexpected: $other"))
    }

  def delete(id: Long): Future[Boolean] =
    domain.ask(ref => Forward<Nombre>(Delete<Nombre>(id, ref))).flatMap {
      case <Nombre>Deleted(_)     => Future.successful(true)
      case <Nombre>NotFound(_)    => Future.successful(false)
      case <Nombre>Error(reason)  => Future.failed(new RuntimeException(reason))
      case other                  => Future.failed(new RuntimeException(s"Unexpected: $other"))
    }

  def list(): Future[Seq[<Nombre>View]] =
    domain.ask(ref => Forward<Nombre>(List<Nombre>s(ref))).flatMap {
      case <Nombre>List(items)    => Future.successful(items)
      case <Nombre>Error(reason)  => Future.failed(new RuntimeException(reason))
      case other                  => Future.failed(new RuntimeException(s"Unexpected: $other"))
    }
}
