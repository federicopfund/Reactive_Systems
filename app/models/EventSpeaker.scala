package models

/**
 * Representa una persona que da la charla / facilita el workshop / participa
 * de una mesa redonda. Se persiste como JSON dentro de
 * `community_events.speakers_json`.
 */
case class EventSpeaker(
  name:   String,
  role:   String         = "",
  avatar: Option[String] = None,
  bio:    String         = ""
) {
  def initials: String = {
    val parts = name.trim.split("\\s+").filter(_.nonEmpty)
    (if (parts.length >= 2) parts(0).take(1) + parts(1).take(1)
     else name.take(2)).toUpperCase
  }
}

object EventSpeaker {
  import play.api.libs.json._
  implicit val format: Format[EventSpeaker] = Json.format[EventSpeaker]

  /** Parser tolerante: si el JSON es invalido, devolvemos lista vacia. */
  def parseAll(json: String): Seq[EventSpeaker] = {
    val trimmed = Option(json).map(_.trim).getOrElse("")
    if (trimmed.isEmpty || trimmed == "[]") Seq.empty
    else scala.util.Try(Json.parse(trimmed).as[Seq[EventSpeaker]]).getOrElse(Seq.empty)
  }

  def writeAll(speakers: Seq[EventSpeaker]): String =
    Json.stringify(Json.toJson(speakers))
}
