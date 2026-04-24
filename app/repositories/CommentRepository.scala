package repositories

import javax.inject.{Inject, Singleton}
import models.{PublicationComment, CommentWithAuthor}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class CommentRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class CommentsTable(tag: Tag) extends Table[PublicationComment](tag, "publication_comments") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def publicationId = column[Long]("publication_id")
    def userId        = column[Long]("user_id")
    def content       = column[String]("content")
    def createdAt     = column[Instant]("created_at")
    def updatedAt     = column[Instant]("updated_at")

    def * = (id.?, publicationId, userId, content, createdAt, updatedAt).mapTo[PublicationComment]
  }

  private class UsersRef(tag: Tag) extends Table[(Long, String, String)](tag, "users") {
    def id       = column[Long]("id", O.PrimaryKey)
    def username = column[String]("username")
    def fullName = column[String]("full_name")
    def * = (id, username, fullName)
  }

  private val comments = TableQuery[CommentsTable]
  private val usersRef = TableQuery[UsersRef]

  /** Create a comment */
  def create(comment: PublicationComment): Future[Long] = {
    val q = comments returning comments.map(_.id)
    db.run(q += comment)
  }

  /** Get comments for a publication with author info */
  def findByPublicationId(publicationId: Long): Future[List[CommentWithAuthor]] = {
    val q = for {
      c <- comments.filter(_.publicationId === publicationId).sortBy(_.createdAt.asc)
      u <- usersRef.filter(_.id === c.userId)
    } yield (c, u.username, u.fullName)

    db.run(q.result).map(_.map { case (c, uname, fname) =>
      CommentWithAuthor(c, uname, fname)
    }.toList)
  }

  /** Count comments for a publication */
  def countByPublicationId(publicationId: Long): Future[Int] = {
    db.run(comments.filter(_.publicationId === publicationId).length.result)
  }

  /** Count comments for multiple publications */
  def countByPublications(pubIds: Seq[Long]): Future[Map[Long, Int]] = {
    if (pubIds.isEmpty) Future.successful(Map.empty)
    else {
      val q = comments
        .filter(_.publicationId.inSet(pubIds))
        .groupBy(_.publicationId)
        .map { case (pid, group) => (pid, group.length) }
      db.run(q.result).map(_.toMap)
    }
  }

  /** Delete a comment (by owner) */
  def delete(commentId: Long, userId: Long): Future[Int] = {
    db.run(comments.filter(c => c.id === commentId && c.userId === userId).delete)
  }

  /** Total comments by a user */
  def countByUserId(userId: Long): Future[Int] = {
    db.run(comments.filter(_.userId === userId).length.result)
  }
}
