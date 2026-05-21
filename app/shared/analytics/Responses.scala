package shared.analytics

import java.time.Instant

/**
 * Protocolo de respuestas del AnalyticsEngine.
 *
 *   - [[MetricsSnapshot]] → snapshot de métricas acumuladas (responde a [[GetMetrics]]).
 */
sealed trait AnalyticsResponse

case class MetricsSnapshot(
  totalEvents:           Long,
  totalPageViews:        Long,
  totalPublicationViews: Long,
  activeUsers:           Set[Long],
  topPages:              Map[String, Long],
  topPublications:       Map[Long, Long],
  eventCounts:           Map[String, Long],
  since:                 Instant
) extends AnalyticsResponse
