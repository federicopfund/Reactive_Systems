package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.ExecutionContext
import java.time.Instant

/**
 * AnalyticsEngine — Actor reactivo para tracking de métricas y eventos.
 *
 * Recopila eventos de uso sin bloquear el flujo principal:
 *   - Page views / publication views
 *   - User actions (login, register, publish, comment)
 *   - Engagement metrics (time on page, interactions)
 *
 * Mantiene contadores in-memory y puede flush a persistencia periódicamente.
 *
 * Principios Reactivos:
 *   - Message-Driven: eventos como mensajes fire-and-forget (tell pattern)
 *   - Elastic: acumula métricas en memoria, procesa sin I/O blocking
 *   - Resilient: actor stateful con snapshot recovery
 *   - Responsive: zero-latency impact en el flujo del usuario
 */

// ── Commands ──
sealed trait AnalyticsCommand

case class TrackEvent(
  eventType: String,
  userId: Option[Long],
  metadata: Map[String, String]
) extends AnalyticsCommand

case class TrackPageView(
  path: String,
  userId: Option[Long],
  referrer: Option[String]
) extends AnalyticsCommand

case class TrackPublicationView(
  publicationId: Long,
  userId: Option[Long]
) extends AnalyticsCommand

case class GetMetrics(
  replyTo: ActorRef[AnalyticsResponse]
) extends AnalyticsCommand

case object ResetMetrics extends AnalyticsCommand

// ── Responses ──
sealed trait AnalyticsResponse
case class MetricsSnapshot(
  totalEvents: Long,
  totalPageViews: Long,
  totalPublicationViews: Long,
  activeUsers: Set[Long],
  topPages: Map[String, Long],
  topPublications: Map[Long, Long],
  eventCounts: Map[String, Long],
  since: Instant
) extends AnalyticsResponse

object AnalyticsEngine {

  private case class State(
    totalEvents: Long = 0,
    totalPageViews: Long = 0,
    totalPublicationViews: Long = 0,
    activeUsers: Set[Long] = Set.empty,
    pageCounts: Map[String, Long] = Map.empty,
    publicationViews: Map[Long, Long] = Map.empty,
    eventCounts: Map[String, Long] = Map.empty,
    since: Instant = Instant.now()
  )

  def apply()(implicit ec: ExecutionContext): Behavior[AnalyticsCommand] =
    active(State())

  private def active(state: State)(implicit ec: ExecutionContext): Behavior[AnalyticsCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        case TrackEvent(eventType, userId, metadata) =>
          context.log.debug(s"[AnalyticsEngine] Event: $eventType ${userId.map(id => s"(user $id)").getOrElse("")}")
          val newState = state.copy(
            totalEvents = state.totalEvents + 1,
            activeUsers = userId.map(state.activeUsers + _).getOrElse(state.activeUsers),
            eventCounts = state.eventCounts.updated(eventType, state.eventCounts.getOrElse(eventType, 0L) + 1)
          )
          active(newState)

        case TrackPageView(path, userId, _) =>
          context.log.debug(s"[AnalyticsEngine] Page view: $path")
          val newState = state.copy(
            totalEvents = state.totalEvents + 1,
            totalPageViews = state.totalPageViews + 1,
            activeUsers = userId.map(state.activeUsers + _).getOrElse(state.activeUsers),
            pageCounts = state.pageCounts.updated(path, state.pageCounts.getOrElse(path, 0L) + 1)
          )
          active(newState)

        case TrackPublicationView(publicationId, userId) =>
          context.log.debug(s"[AnalyticsEngine] Publication view: $publicationId")
          val newState = state.copy(
            totalEvents = state.totalEvents + 1,
            totalPublicationViews = state.totalPublicationViews + 1,
            activeUsers = userId.map(state.activeUsers + _).getOrElse(state.activeUsers),
            publicationViews = state.publicationViews.updated(
              publicationId,
              state.publicationViews.getOrElse(publicationId, 0L) + 1
            )
          )
          active(newState)

        case GetMetrics(replyTo) =>
          context.log.info(s"[AnalyticsEngine] Metrics requested: ${state.totalEvents} total events")
          val top10Pages = state.pageCounts.toSeq.sortBy(-_._2).take(10).toMap
          val top10Pubs = state.publicationViews.toSeq.sortBy(-_._2).take(10).toMap
          replyTo ! MetricsSnapshot(
            totalEvents = state.totalEvents,
            totalPageViews = state.totalPageViews,
            totalPublicationViews = state.totalPublicationViews,
            activeUsers = state.activeUsers,
            topPages = top10Pages,
            topPublications = top10Pubs,
            eventCounts = state.eventCounts,
            since = state.since
          )
          Behaviors.same

        case ResetMetrics =>
          context.log.info("[AnalyticsEngine] Metrics reset")
          active(State())
      }
    }
  }
}
