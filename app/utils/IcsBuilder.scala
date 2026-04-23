package utils

import models.CommunityEvent
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

/**
 * Builder de iCalendar (RFC 5545) para exportar eventos a Google Calendar,
 * Apple Calendar y Outlook.
 *
 * Solo soportamos UTC (`...Z`) en `DTSTART/DTEND` para mantener simple el
 * timezone handling — los clientes lo interpretan correctamente y el detalle
 * se renderiza en la zona del usuario.
 */
object IcsBuilder {

  private val UtcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  private val Prodid = "-//Manifiesto Reactivo//Events 1.0//ES"
  private val Domain = "manifiestoreactivo.ar"

  /** Plegado simple: corta lineas a 75 chars con CRLF + espacio (RFC 5545 sec 3.1). */
  private def fold(line: String): String = {
    if (line.length <= 75) line
    else {
      val sb = new StringBuilder
      var i = 0
      while (i < line.length) {
        val end = math.min(i + (if (i == 0) 75 else 74), line.length)
        if (i > 0) sb.append("\r\n ")
        sb.append(line.substring(i, end))
        i = end
      }
      sb.toString
    }
  }

  /** Escapa caracteres especiales segun RFC 5545. */
  private def escape(value: String): String =
    Option(value).getOrElse("")
      .replace("\\", "\\\\")
      .replace(";", "\\;")
      .replace(",", "\\,")
      .replace("\n", "\\n")
      .replace("\r", "")

  /** Quita HTML basico para DESCRIPTION. */
  private def htmlToText(html: String): String =
    Option(html).getOrElse("")
      .replaceAll("<br\\s*/?>", "\n")
      .replaceAll("</p>", "\n\n")
      .replaceAll("<[^>]+>", "")
      .replaceAll("&nbsp;", " ")
      .replaceAll("&amp;", "&")
      .replaceAll("&lt;", "<")
      .replaceAll("&gt;", ">")
      .trim

  private def stamp(i: Instant): String = UtcStamp.format(i)

  /** Devuelve un VEVENT (sin envoltorio VCALENDAR). */
  def vevent(e: CommunityEvent, baseUrl: String): String = {
    val uid = s"event-${e.id.getOrElse(0L)}@$Domain"
    val now = Instant.now()
    val statusIcs = e.status match {
      case "cancelled" => "CANCELLED"
      case "draft"     => "TENTATIVE"
      case _           => "CONFIRMED"
    }
    val description = {
      val body = htmlToText(e.descriptionHtml)
      val sum  = e.summary.filter(_.nonEmpty).map(_ + "\n\n").getOrElse("")
      escape(sum + body)
    }
    val location = (
      e.locationName.toSeq ++ e.locationDetail.toSeq
    ).filter(_.nonEmpty).mkString(" - ")

    val lines = Seq(
      "BEGIN:VEVENT",
      fold(s"UID:$uid"),
      fold(s"DTSTAMP:${stamp(now)}"),
      fold(s"DTSTART:${stamp(e.startsAt)}"),
      fold(s"DTEND:${stamp(e.endsAt)}"),
      fold(s"SUMMARY:${escape(e.title)}"),
      fold(s"DESCRIPTION:$description"),
      if (location.nonEmpty) Some(fold(s"LOCATION:${escape(location)}")) else None,
      e.locationUrl.filter(_.nonEmpty).map(u => fold(s"URL:${escape(u)}")),
      Some(fold(s"URL;X-LABEL=detail:$baseUrl/eventos/${e.slug}")),
      Some(s"STATUS:$statusIcs"),
      Some("END:VEVENT")
    ).flatMap {
      case s: String       => Some(s)
      case Some(s: String) => Some(s)
      case None            => None
      case _               => None
    }
    lines.mkString("\r\n")
  }

  def calendar(events: Seq[CommunityEvent], baseUrl: String): String = {
    val header = Seq(
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      fold(s"PRODID:$Prodid"),
      "CALSCALE:GREGORIAN",
      "METHOD:PUBLISH"
    ).mkString("\r\n")
    val body = events.map(e => vevent(e, baseUrl)).mkString("\r\n")
    val footer = "END:VCALENDAR"
    Seq(header, body, footer).filter(_.nonEmpty).mkString("\r\n") + "\r\n"
  }

  def single(e: CommunityEvent, baseUrl: String): String = calendar(Seq(e), baseUrl)
}
