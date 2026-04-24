package repositories

import javax.inject.{Inject, Singleton}
import models.{UserBadge, BadgeDefinition}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class BadgeRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class BadgesTable(tag: Tag) extends Table[UserBadge](tag, "user_badges") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId    = column[Long]("user_id")
    def badgeKey  = column[String]("badge_key")
    def awardedAt = column[Instant]("awarded_at")

    def * = (id.?, userId, badgeKey, awardedAt).mapTo[UserBadge]
  }

  private val badges = TableQuery[BadgesTable]

  /** Award a badge (idempotent - ignores if already awarded) */
  def award(userId: Long, badgeKey: String): Future[Boolean] = {
    db.run(badges.filter(b => b.userId === userId && b.badgeKey === badgeKey).exists.result).flatMap {
      case true => Future.successful(false)
      case false =>
        db.run(badges += UserBadge(None, userId, badgeKey)).map(_ => true)
    }
  }

  /** Get all badges for a user */
  def findByUserId(userId: Long): Future[List[UserBadge]] = {
    db.run(badges.filter(_.userId === userId).sortBy(_.awardedAt.desc).result).map(_.toList)
  }

  /** Check if user has a specific badge */
  def hasBadge(userId: Long, badgeKey: String): Future[Boolean] = {
    db.run(badges.filter(b => b.userId === userId && b.badgeKey === badgeKey).exists.result)
  }

  /** Count badges for a user */
  def countByUserId(userId: Long): Future[Int] = {
    db.run(badges.filter(_.userId === userId).length.result)
  }
}
