package models

import java.time.{Instant, LocalDate}

/**
 * Temporada editorial (Issue #22).
 */
case class EditorialSeason(
  id: Option[Long] = None,
  code: String,
  name: String,
  description: Option[String] = None,
  startsOn: Option[LocalDate] = None,
  endsOn: Option[LocalDate] = None,
  isCurrent: Boolean = false,
  createdAt: Instant = Instant.now()
)
