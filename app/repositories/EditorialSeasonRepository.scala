package repositories

import javax.inject.{Inject, Singleton}
import models.EditorialSeason
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
    def startsOn    = column[Option[LocalDate]]("starts_on")
    def endsOn      = column[Option[LocalDate]]("ends_on")
    def isCurrent   = column[Boolean]("is_current")
    def createdAt   = column[Instant]("created_at")

    def * = (
      id.?, code, name, description, startsOn, endsOn, isCurrent, createdAt
    ).mapTo[EditorialSeason]
  }

  private val seasons = TableQuery[EditorialSeasonsTable]

  def findCurrent(): Future[Option[EditorialSeason]] =
    db.run(seasons.filter(_.isCurrent).result.headOption)

  def findAll(): Future[Seq[EditorialSeason]] =
    db.run(seasons.sortBy(_.createdAt.desc).result)

  def findByCode(code: String): Future[Option[EditorialSeason]] =
    db.run(seasons.filter(_.code === code).result.headOption)
}
