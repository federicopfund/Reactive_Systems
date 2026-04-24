package controllers

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import core.guardian._
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * HealthController — Endpoint operacional respaldado por los 3 guardians (Issue #15).
 *
 * `GET /health`         → 200/503 con flag `healthy` por capa (resumido).
 * `GET /health/detail`  → 200 con detalle por hijo: estado, reinicios, latencia.
 *
 * Sin acceso a base de datos; solo consulta a los actores en paralelo con
 * timeout de 3s.
 */
@Singleton
class HealthController @Inject()(
  cc:       ControllerComponents,
  domain:   ActorRef[DomainGuardianCommand],
  crossCut: ActorRef[CrossCutGuardianCommand],
  infra:    ActorRef[InfraGuardianCommand]
)(implicit ec: ExecutionContext, scheduler: Scheduler) extends AbstractController(cc) {

  private implicit val timeout: Timeout = 3.seconds

  private def gatherAll(): Future[Seq[GuardianHealth]] = {
    val d  = domain.ask[GuardianHealth](GetDomainHealth(_))
    val c  = crossCut.ask[GuardianHealth](GetCrossCutHealth(_))
    val i  = infra.ask[GuardianHealth](GetInfraHealth(_))
    Future.sequence(Seq(d, c, i))
  }

  def health: Action[AnyContent] = Action.async { _ =>
    gatherAll().map { layers =>
      val flags = layers.map(l => l.layer -> l.healthy).toMap
      val ok    = layers.forall(_.healthy)
      val body  = Json.obj(
        "status" -> (if (ok) "healthy" else "down"),
        "capas"  -> Json.toJson(flags)
      )
      if (ok) Ok(body) else ServiceUnavailable(body)
    }.recover { case ex =>
      ServiceUnavailable(Json.obj("status" -> "down", "error" -> ex.getMessage))
    }
  }

  def healthDetail: Action[AnyContent] = Action.async { _ =>
    gatherAll().map { layers =>
      Ok(Json.obj(
        "capas" -> Json.toJson(layers.map { l =>
          Json.obj(
            "capa"    -> l.layer,
            "healthy" -> l.healthy,
            "hijos"   -> Json.toJson(l.children.map { case (name, h) =>
              name -> Json.obj(
                "status"      -> h.status.name,
                "reinicios"   -> h.restarts,
                "ultimoError" -> h.lastError,
                "ultimoPingMs"-> h.lastPingMs,
                "updatedAt"   -> h.updatedAt.toString
              )
            })
          )
        })
      ))
    }.recover { case ex =>
      ServiceUnavailable(Json.obj("error" -> ex.getMessage))
    }
  }
}
