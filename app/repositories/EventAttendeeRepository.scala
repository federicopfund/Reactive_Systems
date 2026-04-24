package repositories

import javax.inject.{Inject, Singleton}
import models.EventAttendee
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * RSVPs sobre eventos (Issue #19). Una fila por (event, user) — UNIQUE en la
 * migracion 25.sql. `upsertRsvp` resuelve insert-or-update tomando ese par.
 */
@Singleton
class EventAttendeeRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Attendees(tag: Tag) extends Table[EventAttendee](tag, "event_attendees") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def eventId       = column[Long]("event_id")
    def userId        = column[Long]("user_id")
    def rsvpStatus    = column[String]("rsvp_status")
    def reminderOptin = column[Boolean]("reminder_optin")
    def notes         = column[Option[String]]("notes")
    def createdAt     = column[Instant]("created_at")
    def updatedAt     = column[Instant]("updated_at")

    def * = (
      id.?, eventId, userId, rsvpStatus, reminderOptin, notes, createdAt, updatedAt
    ).mapTo[EventAttendee]
  }

  private val attendees = TableQuery[Attendees]

  def upsertRsvp(eventId: Long, userId: Long, status: String, reminderOptin: Boolean): Future[Int] = {
    val now = Instant.now()
    db.run(
      attendees.filter(a => a.eventId === eventId && a.userId === userId).result.headOption
    ).flatMap {
      case Some(existing) =>
        val u = existing.copy(rsvpStatus = status, reminderOptin = reminderOptin, updatedAt = now)
        db.run(attendees.filter(_.id === existing.id.get).update(u))
      case None =>
        val a = EventAttendee(
          eventId = eventId, userId = userId,
          rsvpStatus = status, reminderOptin = reminderOptin,
          createdAt = now, updatedAt = now
        )
        db.run(attendees += a)
    }
  }

  def toggleReminder(eventId: Long, userId: Long, optIn: Boolean): Future[Int] =
    db.run(
      attendees
        .filter(a => a.eventId === eventId && a.userId === userId)
        .map(a => (a.reminderOptin, a.updatedAt))
        .update((optIn, Instant.now()))
    )

  def findByEvent(eventId: Long, statusFilter: Option[String] = None): Future[Seq[EventAttendee]] = {
    val base = attendees.filter(_.eventId === eventId)
    val q = statusFilter match {
      case Some(s) if s.nonEmpty => base.filter(_.rsvpStatus === s)
      case _                     => base
    }
    db.run(q.sortBy(_.createdAt.desc).result)
  }

  def findByUser(userId: Long): Future[Seq[EventAttendee]] =
    db.run(attendees.filter(_.userId === userId).sortBy(_.createdAt.desc).result)

  def findForUserAndEvent(eventId: Long, userId: Long): Future[Option[EventAttendee]] =
    db.run(attendees.filter(a => a.eventId === eventId && a.userId === userId).result.headOption)

  def countByStatus(eventId: Long): Future[Map[String, Int]] = db.run(
    attendees.filter(_.eventId === eventId).groupBy(_.rsvpStatus).map {
      case (s, rows) => (s, rows.length)
    }.result
  ).map(_.toMap)
}
