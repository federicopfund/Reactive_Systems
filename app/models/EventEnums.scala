package models

/**
 * Enumeraciones del modulo de Eventos (Issue #19).
 *
 * Se modelan como objetos con `keys: Seq[String]` y helpers `label`/`icon`
 * en vez de `Enumeration` para alinearse con el patron usado en
 * `NotificationType` y permitir mapping liviano desde la columna VARCHAR.
 */
object EventEnums {

  // ── Tipo de evento ────────────────────────────────────────────────
  object EventType {
    val Talk       = "talk"
    val Workshop   = "workshop"
    val Meetup     = "meetup"
    val Ama        = "ama"
    val Release    = "release"
    val Stream     = "stream"
    val Roundtable = "roundtable"

    val all: Seq[String] = Seq(Talk, Workshop, Meetup, Ama, Release, Stream, Roundtable)

    def label(key: String): String = key match {
      case Talk       => "Charla"
      case Workshop   => "Workshop"
      case Meetup     => "Meetup"
      case Ama        => "AMA"
      case Release    => "Release"
      case Stream     => "Stream"
      case Roundtable => "Mesa redonda"
      case other      => other.capitalize
    }

    /** Color token del design system asociado por defecto. */
    def accentToken(key: String): String = key match {
      case Talk | Roundtable => "responsive"
      case Workshop          => "elastic"
      case Meetup | Ama      => "message"
      case Release | Stream  => "resilient"
      case _                 => "accent"
    }
  }

  // ── Modalidad ─────────────────────────────────────────────────────
  object Modality {
    val Online     = "online"
    val Presencial = "presencial"
    val Hibrido    = "hibrido"

    val all: Seq[String] = Seq(Online, Presencial, Hibrido)

    def label(key: String): String = key match {
      case Online     => "Online"
      case Presencial => "Presencial"
      case Hibrido    => "Hibrido"
      case other      => other.capitalize
    }
  }

  // ── Estado del evento ─────────────────────────────────────────────
  object EventStatus {
    val Draft     = "draft"
    val Published = "published"
    val Cancelled = "cancelled"
    val Archived  = "archived"

    val all: Seq[String] = Seq(Draft, Published, Cancelled, Archived)

    def label(key: String): String = key match {
      case Draft     => "Borrador"
      case Published => "Publicado"
      case Cancelled => "Cancelado"
      case Archived  => "Archivado"
      case other     => other.capitalize
    }

    /** Transiciones validas desde un estado dado. */
    def allowedNext(current: String): Set[String] = current match {
      case Draft     => Set(Published, Cancelled, Archived)
      case Published => Set(Cancelled, Archived)
      case Cancelled => Set(Archived)
      case Archived  => Set.empty
      case _         => Set.empty
    }
  }

  // ── RSVP ──────────────────────────────────────────────────────────
  object RsvpStatus {
    val Attending = "attending"
    val Maybe     = "maybe"
    val Declined  = "declined"

    val all: Seq[String] = Seq(Attending, Maybe, Declined)

    def label(key: String): String = key match {
      case Attending => "Asistire"
      case Maybe     => "Quizas"
      case Declined  => "No podre"
      case other     => other.capitalize
    }
  }
}
