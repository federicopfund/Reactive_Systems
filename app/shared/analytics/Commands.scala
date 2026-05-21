package shared.analytics

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del AnalyticsEngine.
 *
 * Todos los comandos son públicos (fire-and-forget o ask).
 * No hay comandos internos ya que el actor no usa pipeToSelf.
 */
sealed trait AnalyticsCommand

case class TrackEvent(
  eventType: String,
  userId:    Option[Long],
  metadata:  Map[String, String]
) extends AnalyticsCommand

case class TrackPageView(
  path:     String,
  userId:   Option[Long],
  referrer: Option[String]
) extends AnalyticsCommand

case class TrackPublicationView(
  publicationId: Long,
  userId:        Option[Long]
) extends AnalyticsCommand

case class GetMetrics(
  replyTo: ActorRef[AnalyticsResponse]
) extends AnalyticsCommand

case object ResetMetrics extends AnalyticsCommand
