package domains.editorial.repositories

import javax.inject.{Inject, Singleton}
import domains.editorial.models.EditorialSeason
import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class EditorialSeasonRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class EditorialSeasonsTable(tag: Tag)
      extends Table[EditorialSeason](tag, "editorial_seasons") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def code        = column[String]("code")
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def tagline     = column[Option[String]]("tagline")
    def openingEssay = column[Option[String]]("opening_essay")
    def startsOn    = column[Option[LocalDate]]("starts_on")
    def endsOn      = column[Option[LocalDate]]("ends_on")
    def isCurrent   = column[Boolean]("is_current")
    def createdAt   = column[Instant]("created_at")

    def * = (
      id.?, code, name, description, tagline, openingEssay, startsOn, endsOn, isCurrent, createdAt
    ).mapTo[EditorialSeason]
  }

  private val seasons = TableQuery[EditorialSeasonsTable]

  def findCurrent(): Future[Option[EditorialSeason]] =
    db.run(seasons.filter(_.isCurrent).result.headOption)

  def findAll(): Future[Seq[EditorialSeason]] =
    db.run(seasons.sortBy(_.createdAt.desc).result)

  def findAllChronologicalDesc(): Future[Seq[EditorialSeason]] =
    db.run(seasons.sortBy(s => (s.startsOn.desc.nullsLast, s.createdAt.desc)).result)

  def findByCode(code: String): Future[Option[EditorialSeason]] =
    db.run(seasons.filter(_.code === code).result.headOption)

  def findById(id: Long): Future[Option[EditorialSeason]] =
    db.run(seasons.filter(_.id === id).result.headOption)

  def create(
    code: String,
    name: String,
    tagline: Option[String],
    openingEssay: Option[String],
    startsOn: Option[LocalDate],
    endsOn: Option[LocalDate]
  ): Future[Long] = {
    val row = EditorialSeason(
      code = code,
      name = name,
      tagline = tagline,
      openingEssay = openingEssay,
      startsOn = startsOn,
      endsOn = endsOn,
      isCurrent = false
    )
    db.run((seasons returning seasons.map(_.id)) += row)
  }

  def updateBasic(
    id: Long,
    name: String,
    tagline: Option[String],
    openingEssay: Option[String],
    startsOn: Option[LocalDate],
    endsOn: Option[LocalDate]
  ): Future[Int] =
    db.run(
      seasons.filter(_.id === id)
        .map(s => (s.name, s.tagline, s.openingEssay, s.startsOn, s.endsOn))
        .update((name, tagline, openingEssay, startsOn, endsOn))
    )

  def setCurrent(id: Long): Future[Boolean] = {
    val action = for {
      targetOpt <- seasons.filter(_.id === id).result.headOption
      updated <- targetOpt match {
        case None => DBIO.successful(false)
        case Some(_) =>
          for {
            _ <- seasons.map(_.isCurrent).update(false)
            n <- seasons.filter(_.id === id).map(_.isCurrent).update(true)
          } yield n > 0
      }
    } yield updated

    db.run(action.transactionally)
  }
}
