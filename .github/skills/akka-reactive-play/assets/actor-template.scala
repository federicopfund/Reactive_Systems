package domains.<dominio>

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import java.time.Instant

// ── Protocol (ADT sellado) ──────────────────────────────────────────────────

sealed trait <Nombre>Command

/** Comando mutante — fire and forget */
case class Execute<Nombre>(
  payload: String
) extends <Nombre>Command

/** Consulta — siempre incluye replyTo */
case class Get<Nombre>State(
  replyTo: ActorRef[<Nombre>Response]
) extends <Nombre>Command

/** Mensajes internos — nunca exponer fuera del objeto */
private[<dominio>] case class InternalResult(value: String) extends <Nombre>Command
private[<dominio>] case class InternalError(reason: String) extends <Nombre>Command

// ── Responses ──────────────────────────────────────────────────────────────

sealed trait <Nombre>Response

case class <Nombre>StateSnapshot(
  items: List[String],
  processedCount: Long,
  since: Instant
) extends <Nombre>Response

// ── Actor implementation ───────────────────────────────────────────────────

object <Nombre>Actor {

  // Estado completamente inmutable
  private case class State(
    items: List[String]      = List.empty,
    processedCount: Long     = 0L,
    since: Instant           = Instant.now()
  )

  /** Entry point — inyectar dependencias como parámetros, no como `var` globales */
  def apply()(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    active(State())

  private def active(state: State)(implicit ec: ExecutionContext): Behavior[<Nombre>Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {

        // ── Comando mutante ────────────────────────────────────────────────
        case Execute<Nombre>(payload) =>
          // Operación async sin bloquear el actor
          val op: Future[String] = Future.successful(payload.toUpperCase) // reemplazar con lógica real
          ctx.pipeToSelf(op) {
            case Success(result) => InternalResult(result)
            case Failure(e)      => InternalError(e.getMessage)
          }
          Behaviors.same // no cambia estado hasta recibir InternalResult

        // ── Resultado interno ──────────────────────────────────────────────
        case InternalResult(value) =>
          ctx.log.info("Processed: {}", value)
          active(state.copy(
            items          = value :: state.items,
            processedCount = state.processedCount + 1
          ))

        case InternalError(reason) =>
          ctx.log.error("Processing failed: {}", reason)
          Behaviors.same // resilient: absorbe el error, no lanza excepción

        // ── Consulta ───────────────────────────────────────────────────────
        case Get<Nombre>State(replyTo) =>
          replyTo ! <Nombre>StateSnapshot(
            items          = state.items,
            processedCount = state.processedCount,
            since          = state.since
          )
          Behaviors.same
      }
    }
}
