package repositories

import javax.inject.{Inject, Singleton}
import models.{UserBookmark, Publication, PublicationWithAuthor}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class BookmarkRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class BookmarksTable(tag: Tag) extends Table[UserBookmark](tag, "user_bookmarks") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId        = column[Long]("user_id")
    def publicationId = column[Long]("publication_id")
    def createdAt     = column[Instant]("created_at")

    def * = (id.?, userId, publicationId, createdAt).mapTo[UserBookmark]
  }

  private val bookmarks = TableQuery[BookmarksTable]

  /** Toggle bookmark: add if missing, remove if exists. Returns true if added. */
  def toggle(userId: Long, publicationId: Long): Future[Boolean] = {
    val existing = bookmarks.filter(b =>
      b.userId === userId && b.publicationId === publicationId
    )
    db.run(existing.result.headOption).flatMap {
      case Some(_) =>
        db.run(existing.delete).map(_ => false)
      case None =>
        val bm = UserBookmark(None, userId, publicationId)
        db.run(bookmarks += bm).map(_ => true)
    }
  }

  /** Check if a user has bookmarked a publication */
  def isBookmarked(userId: Long, publicationId: Long): Future[Boolean] = {
    db.run(bookmarks.filter(b =>
      b.userId === userId && b.publicationId === publicationId
    ).exists.result)
  }

  /** Get all bookmarked publication IDs for a user */
  def getBookmarkedIds(userId: Long): Future[Set[Long]] = {
    db.run(bookmarks.filter(_.userId === userId).map(_.publicationId).result).map(_.toSet)
  }

  /** Get all bookmarks for a user */
  def findByUserId(userId: Long): Future[List[UserBookmark]] = {
    db.run(bookmarks.filter(_.userId === userId).sortBy(_.createdAt.desc).result).map(_.toList)
  }

  /** Count bookmarks for a user */
  def countByUserId(userId: Long): Future[Int] = {
    db.run(bookmarks.filter(_.userId === userId).length.result)
  }
}
