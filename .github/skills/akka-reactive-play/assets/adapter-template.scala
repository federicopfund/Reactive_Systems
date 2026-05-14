package domains.<dominio>

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import infrastructure.guardian.CrossCutGuardianCommand
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Reactive<Nombre>Adapter — Fachada inyectable que desacopla Play del ActorRef.
 *
 * El controller sólo conoce esta clase, nunca el ActorRef directamente.
 * Expone Futures; el ask timeout está configurado para ser menor
 * que el timeout del request HTTP de Play (default 60s).
 */
class Reactive<Nombre>Adapter(
  guardian: ActorRef[CrossCutGuardianCommand],
  scheduler: Scheduler,
  ec: ExecutionContext
) {
  implicit private val timeout: Timeout         = 5.seconds
  implicit private val s: Scheduler             = scheduler
  implicit private val e: ExecutionContext       = ec

  /** Envia un comando al actor vía el guardian */
  def execute(payload: String): Future[Unit] = {
    guardian.ask[Unit](ref => Forward<Nombre>Command(Execute<Nombre>(payload)))
  }

  /** Consulta el estado actual del actor */
  def getState(): Future[<Nombre>StateSnapshot] = {
    guardian.ask[<Nombre>StateSnapshot](ref =>
      Forward<Nombre>Query(Get<Nombre>State(ref))
    )
  }
}

// Mensaje de reenvío en CrossCutGuardian (agregar al sealed trait CrossCutGuardianCommand):
// case class Forward<Nombre>Command(cmd: <Nombre>Command)       extends CrossCutGuardianCommand
// case class Forward<Nombre>Query(cmd: <Nombre>Command)         extends CrossCutGuardianCommand
