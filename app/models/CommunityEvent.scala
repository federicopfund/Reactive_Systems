package models

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Evento de la comunidad — Issue #19.
 *
 * Pieza editorial con ciclo: draft -> published -> cancelled / archived.
 * Los `speakers` se persisten como JSON en `speakers_json` (helper
 * `EventSpeaker.parseAll`).
 */
case class CommunityEvent(
  id:                 Option[Long]    = None,
  slug:               String,
  title:              String,
  summary:            Option[String]  = None,
  descriptionHtml:    String,

  eventType:          String          = EventEnums.EventType.Talk,
  modality:           String          = EventEnums.Modality.Online,

  startsAt:           Instant,
  endsAt:             Instant,
  timezone:           String          = "America/Argentina/Cordoba",

  locationName:       Option[String]  = None,
  locationUrl:        Option[String]  = None,
  locationDetail:     Option[String]  = None,

  coverImage:         Option[String]  = None,
  accentColor:        Option[String]  = None,

  capacity:           Option[Int]     = None,
  tagsPipe:           String          = "",
  speakersJson:       String          = "[]",

  status:             String          = EventEnums.EventStatus.Draft,
  cancellationReason: Option[String]  = None,

  createdBy:          Long,
  publishedBy:        Option[Long]    = None,
  publishedAt:        Option[Instant] = None,

  viewCount:          Int             = 0,
  createdAt:          Instant         = Instant.now(),
  updatedAt:          Instant         = Instant.now()
) {

  def tags: Seq[String] =
    if (tagsPipe.isEmpty) Seq.empty
    else tagsPipe.split('|').toSeq.map(_.trim).filter(_.nonEmpty)

  def speakers: Seq[EventSpeaker] = EventSpeaker.parseAll(speakersJson)

  def zoneId: ZoneId = scala.util.Try(ZoneId.of(timezone)).getOrElse(ZoneId.systemDefault())

  def localStart: LocalDateTime = startsAt.atZone(zoneId).toLocalDateTime
  def localEnd:   LocalDateTime = endsAt.atZone(zoneId).toLocalDateTime

  def isPast:     Boolean = endsAt.isBefore(Instant.now())
  def isLive:     Boolean = !isPast && !startsAt.isAfter(Instant.now())
  def isUpcoming: Boolean = startsAt.isAfter(Instant.now())

  def durationMinutes: Long =
    ChronoUnit.MINUTES.between(startsAt, endsAt)

  /** Clave AAAA-MM para agrupar en cabeceras de cronograma. */
  def monthKey: String = {
    val ld = localStart.toLocalDate
    f"${ld.getYear}%04d-${ld.getMonthValue}%02d"
  }

  def localDate: LocalDate = localStart.toLocalDate

  /** Ej: "20:00 - 21:30 - GMT-3" */
  def timeRangeLabel: String = {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val s = localStart.format(fmt)
    val e = localEnd.format(fmt)
    s"$s - $e"
  }

  def accentToken: String =
    accentColor.filter(_.nonEmpty)
      .getOrElse(EventEnums.EventType.accentToken(eventType))
}
