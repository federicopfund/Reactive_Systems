package domains.<dominio>.engines

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domains.<dominio>.models._
import domains.<dominio>.repositories.<Nombre>Repository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

// ── Protocol ──────────────────────────────────────────────────────────────

sealed trait <Nombre>Command
sealed trait <Nombre>Response

// Comandos públicos
case class Create<Nombre>(req: Create<Nombre>Request, replyTo: ActorRef[<Nombre>Response]) extends <Nombre>Command
case class Get<Nombre>(id: Long, replyTo: ActorRef[<Nombre>Response])                      extends <Nombre>Command
case class Update<Nombre>(id: Long, req: Update<Nombre>Request, replyTo: ActorRef[<Nombre>Response]) extends <Nombre>Command
case class Delete<Nombre>(id: Long, replyTo: ActorRef[<Nombre>Response])                   extends <Nombre>Command
case class List<Nombre>s(replyTo: ActorRef[<Nombre>Response])                              extends <Nombre>Command

// Respuestas públicas
case class <Nombre>Created(view: <Nombre>View)        extends <Nombre>Response
case class <Nombre>Found(view: <Nombre>View)          extends <Nombre>Response
case class <Nombre>Updated(view: <Nombre>View)        extends <Nombre>Response
case class <Nombre>Deleted(id: Long)                  extends <Nombre>Response
case class <Nombre>List(items: Seq[<Nombre>View])     extends <Nombre>Response
case class <Nombre>NotFound(id: Long)                 extends <Nombre>Response
case class <Nombre>Error(reason: String)              extends <Nombre>Response

// Mensajes internos — NO exponer fuera del engine
private[engines] sealed trait Internal extends <Nombre>Command
private[engines] case class InternalCreated(entity: <Nombre>, replyTo: ActorRef[<Nombre>Response])    extends Internal
private[engines] case class InternalFound(opt: Option[<Nombre>], id: Long, replyTo: ActorRef[<Nombre>Response]) extends Internal
private[engines] case class InternalUpdated(opt: Option[<Nombre>], replyTo: ActorRef[<Nombre>Response]) extends Internal
private[engines] case class InternalDeleted(found: Boolean, id: Long, replyTo: ActorRef[<Nombre>Response]) extends Internal
private[engines] case class InternalList(items: Seq[<Nombre>], replyTo: ActorRef[<Nombre>Response])   extends Internal
private[engines] case class InternalError(reason: String, replyTo: ActorRef[<Nombre>Response])        extends Internal

// ── Engine ────────────────────────────────────────────────────────────────

object <Nombre>Engine {

  def apply(repo: <Nombre>Repository)(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    active(repo)

  private def active(repo: <Nombre>Repository)(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case Create<Nombre>(req, replyTo) =>
          ctx.pipeToSelf(repo.create(req)) {
            case Success(entity) => InternalCreated(entity, replyTo)
            case Failure(e)      => InternalError(e.getMessage, replyTo)
          }
          Behaviors.same

        case Get<Nombre>(id, replyTo) =>
          ctx.pipeToSelf(repo.findById(id)) {
            case Success(opt) => InternalFound(opt, id, replyTo)
            case Failure(e)   => InternalError(e.getMessage, replyTo)
          }
          Behaviors.same

        case Update<Nombre>(id, req, replyTo) =>
          ctx.pipeToSelf(repo.update(id, req)) {
            case Success(opt) => InternalUpdated(opt, replyTo)
            case Failure(e)   => InternalError(e.getMessage, replyTo)
          }
          Behaviors.same

        case Delete<Nombre>(id, replyTo) =>
          ctx.pipeToSelf(repo.delete(id)) {
            case Success(found) => InternalDeleted(found, id, replyTo)
            case Failure(e)     => InternalError(e.getMessage, replyTo)
          }
          Behaviors.same

        case List<Nombre>s(replyTo) =>
          ctx.pipeToSelf(repo.findAll()) {
            case Success(items) => InternalList(items, replyTo)
            case Failure(e)     => InternalError(e.getMessage, replyTo)
          }
          Behaviors.same

        // ── Resultados internos ───────────────────────────────────────────

        case InternalCreated(entity, replyTo) =>
          replyTo ! <Nombre>Created(<Nombre>View.from(entity))
          Behaviors.same

        case InternalFound(Some(entity), _, replyTo) =>
          replyTo ! <Nombre>Found(<Nombre>View.from(entity))
          Behaviors.same

        case InternalFound(None, id, replyTo) =>
          replyTo ! <Nombre>NotFound(id)
          Behaviors.same

        case InternalUpdated(Some(entity), replyTo) =>
          replyTo ! <Nombre>Updated(<Nombre>View.from(entity))
          Behaviors.same

        case InternalUpdated(None, replyTo) =>
          replyTo ! <Nombre>NotFound(-1L)
          Behaviors.same

        case InternalDeleted(true, id, replyTo) =>
          replyTo ! <Nombre>Deleted(id)
          Behaviors.same

        case InternalDeleted(false, id, replyTo) =>
          replyTo ! <Nombre>NotFound(id)
          Behaviors.same

        case InternalList(items, replyTo) =>
          replyTo ! <Nombre>List(items.map(<Nombre>View.from))
          Behaviors.same

        case InternalError(reason, replyTo) =>
          ctx.log.error("Engine error: {}", reason)
          replyTo ! <Nombre>Error(reason)
          Behaviors.same
      }
    }
}
