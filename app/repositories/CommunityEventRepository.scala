package repositories

import javax.inject.{Inject, Singleton}
import models.{CommunityEvent, EventEnums}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Persistencia de `community_events` (Issue #19).
 *
 * Cubre el CRUD del backoffice + las consultas publicas para vista mes,
 * cronograma y proximos eventos. Los conteos por estado alimentan los
 * KPIs del dashboard.
 */
@Singleton
class CommunityEventRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Events(tag: Tag) extends Table[CommunityEvent](tag, "community_events") {
    def id                 = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug               = column[String]("slug")
    def title              = column[String]("title")
    def summary            = column[Option[String]]("summary")
    def descriptionHtml    = column[String]("description_html")
    def eventType          = column[String]("event_type")
    def modality           = column[String]("modality")
    def startsAt           = column[Instant]("starts_at")
    def endsAt             = column[Instant]("ends_at")
    def timezone           = column[String]("timezone")
    def locationName       = column[Option[String]]("location_name")
    def locationUrl        = column[Option[String]]("location_url")
    def locationDetail     = column[Option[String]]("location_detail")
    def coverImage         = column[Option[String]]("cover_image")
    def accentColor        = column[Option[String]]("accent_color")
    def capacity           = column[Option[Int]]("capacity")
    def tagsPipe           = column[String]("tags_pipe")
    def speakersJson       = column[String]("speakers_json")
    def status             = column[String]("status")
    def cancellationReason = column[Option[String]]("cancellation_reason")
    def createdBy          = column[Long]("created_by")
    def publishedBy        = column[Option[Long]]("published_by")
    def publishedAt        = column[Option[Instant]]("published_at")
    def viewCount          = column[Int]("view_count")
    def createdAt          = column[Instant]("created_at")
    def updatedAt          = column[Instant]("updated_at")

    def * = (
      (id.?, slug, title, summary, descriptionHtml, eventType, modality, startsAt, endsAt, timezone),
      (locationName, locationUrl, locationDetail, coverImage, accentColor, capacity, tagsPipe, speakersJson),
      (status, cancellationReason, createdBy, publishedBy, publishedAt, viewCount, createdAt, updatedAt)
    ).shaped <> ({
      case (a, b, c) => CommunityEvent(
        id = a._1, slug = a._2, title = a._3, summary = a._4, descriptionHtml = a._5,
        eventType = a._6, modality = a._7, startsAt = a._8, endsAt = a._9, timezone = a._10,
        locationName = b._1, locationUrl = b._2, locationDetail = b._3,
        coverImage = b._4, accentColor = b._5,
        capacity = b._6, tagsPipe = b._7, speakersJson = b._8,
        status = c._1, cancellationReason = c._2,
        createdBy = c._3, publishedBy = c._4, publishedAt = c._5,
        viewCount = c._6, createdAt = c._7, updatedAt = c._8
      )
    }, { ce: CommunityEvent =>
      Some((
        (ce.id, ce.slug, ce.title, ce.summary, ce.descriptionHtml,
         ce.eventType, ce.modality, ce.startsAt, ce.endsAt, ce.timezone),
        (ce.locationName, ce.locationUrl, ce.locationDetail,
         ce.coverImage, ce.accentColor, ce.capacity, ce.tagsPipe, ce.speakersJson),
        (ce.status, ce.cancellationReason, ce.createdBy, ce.publishedBy,
         ce.publishedAt, ce.viewCount, ce.createdAt, ce.updatedAt)
      ))
    })
  }

  private val events = TableQuery[Events]

  // ── CRUD ──
  def create(e: CommunityEvent): Future[Long] = {
    val q = events returning events.map(_.id)
    db.run(q += e)
  }

  def update(e: CommunityEvent): Future[Int] = e.id match {
    case Some(id) =>
      val now = Instant.now()
      db.run(events.filter(_.id === id).update(e.copy(updatedAt = now)))
    case None => Future.successful(0)
  }

  def delete(id: Long): Future[Int] =
    db.run(events.filter(_.id === id).delete)

  def findById(id: Long): Future[Option[CommunityEvent]] =
    db.run(events.filter(_.id === id).result.headOption)

  def findBySlug(slug: String): Future[Option[CommunityEvent]] =
    db.run(events.filter(_.slug === slug).result.headOption)

  def slugExists(slug: String, excluding: Option[Long] = None): Future[Boolean] = {
    val base = events.filter(_.slug === slug)
    val q = excluding match {
      case Some(id) => base.filter(_.id =!= id)
      case None     => base
    }
    db.run(q.exists.result)
  }

  // ── Consultas publicas ──
  def findPublishedUpcoming(limit: Int = 10): Future[Seq[CommunityEvent]] = {
    val now = Instant.now()
    db.run(
      events
        .filter(e => e.status === EventEnums.EventStatus.Published && e.startsAt >= now)
        .sortBy(_.startsAt.asc)
        .take(limit)
        .result
    )
  }

  def findPublishedByRange(from: Instant, to: Instant): Future[Seq[CommunityEvent]] =
    db.run(
      events
        .filter(e =>
          e.status === EventEnums.EventStatus.Published &&
          e.startsAt >= from && e.startsAt < to
        )
        .sortBy(_.startsAt.asc)
        .result
    )

  def findPublishedPast(limit: Int = 50, offset: Int = 0): Future[Seq[CommunityEvent]] = {
    val now = Instant.now()
    db.run(
      events
        .filter(e =>
          e.status === EventEnums.EventStatus.Published && e.endsAt < now
        )
        .sortBy(_.startsAt.desc)
        .drop(offset).take(limit)
        .result
    )
  }

  def findPublishedAll(limit: Int = 100): Future[Seq[CommunityEvent]] =
    db.run(
      events
        .filter(_.status === EventEnums.EventStatus.Published)
        .sortBy(_.startsAt.asc)
        .take(limit)
        .result
    )

  // ── Backoffice ──
  def findAllForAdmin(
    statusFilter: Option[String] = None,
    page: Int = 0,
    pageSize: Int = 20
  ): Future[Seq[CommunityEvent]] = {
    val base = statusFilter match {
      case Some(s) if s.nonEmpty => events.filter(_.status === s)
      case _                     => events
    }
    db.run(
      base.sortBy(_.startsAt.desc)
        .drop(page * pageSize).take(pageSize)
        .result
    )
  }

  def countForAdmin(statusFilter: Option[String] = None): Future[Int] = {
    val base = statusFilter match {
      case Some(s) if s.nonEmpty => events.filter(_.status === s)
      case _                     => events
    }
    db.run(base.length.result)
  }

  def countByStatus(): Future[Map[String, Int]] = db.run(
    events.groupBy(_.status).map { case (s, rows) => (s, rows.length) }.result
  ).map(_.toMap)

  def changeStatus(id: Long, newStatus: String, actorId: Long, reason: Option[String] = None): Future[Int] = {
    val now = Instant.now()
    findById(id).flatMap {
      case Some(e) if EventEnums.EventStatus.allowedNext(e.status).contains(newStatus) =>
        val updated = e.copy(
          status = newStatus,
          cancellationReason =
            if (newStatus == EventEnums.EventStatus.Cancelled) reason.orElse(e.cancellationReason)
            else e.cancellationReason,
          publishedBy =
            if (newStatus == EventEnums.EventStatus.Published) Some(actorId) else e.publishedBy,
          publishedAt =
            if (newStatus == EventEnums.EventStatus.Published) Some(now) else e.publishedAt,
          updatedAt = now
        )
        db.run(events.filter(_.id === id).update(updated))
      case _ => Future.successful(0)
    }
  }

  def incrementViewCount(id: Long): Future[Int] =
    db.run(
      sqlu"""UPDATE community_events SET view_count = view_count + 1 WHERE id = $id"""
    )

  def countUpcomingThisWeek(): Future[Int] = {
    val now = Instant.now()
    val plus7 = now.plusSeconds(7L * 24 * 3600)
    db.run(
      events
        .filter(e =>
          e.status === EventEnums.EventStatus.Published &&
          e.startsAt >= now && e.startsAt < plus7
        )
        .length.result
    )
  }
}
