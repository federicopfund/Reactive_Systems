package models

import java.time.Instant

/**
 * RSVP de un usuario sobre un evento (Issue #19). Una fila por par (event,user)
 * gracias al UNIQUE en la migracion 25.sql.
 */
case class EventAttendee(
  id:            Option[Long]   = None,
  eventId:       Long,
  userId:        Long,
  rsvpStatus:    String,
  reminderOptin: Boolean        = true,
  notes:         Option[String] = None,
  createdAt:     Instant        = Instant.now(),
  updatedAt:     Instant        = Instant.now()
)
