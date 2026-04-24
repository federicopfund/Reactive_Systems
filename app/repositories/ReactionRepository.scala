package repositories

import javax.inject.{Inject, Singleton}
import models.PublicationReaction
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class ReactionRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class ReactionsTable(tag: Tag) extends Table[PublicationReaction](tag, "publication_reactions") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def publicationId = column[Long]("publication_id")
    def userId        = column[Long]("user_id")
    def reactionType  = column[String]("reaction_type")
    def createdAt     = column[Instant]("created_at")

    def * = (id.?, publicationId, userId, reactionType, createdAt).mapTo[PublicationReaction]
  }

  private val reactions = TableQuery[ReactionsTable]

  /** Toggle reaction: add if missing, remove if exists. Returns true if added. */
  def toggle(publicationId: Long, userId: Long, reactionType: String): Future[Boolean] = {
    val existing = reactions.filter(r =>
      r.publicationId === publicationId &&
      r.userId === userId &&
      r.reactionType === reactionType
    )
    db.run(existing.result.headOption).flatMap {
      case Some(_) =>
        db.run(existing.delete).map(_ => false)
      case None =>
        val r = PublicationReaction(None, publicationId, userId, reactionType)
        db.run(reactions += r).map(_ => true)
    }
  }

  /** Count reactions by type for a publication */
  def countByPublication(publicationId: Long): Future[Map[String, Int]] = {
    val q = reactions
      .filter(_.publicationId === publicationId)
      .groupBy(_.reactionType)
      .map { case (rt, group) => (rt, group.length) }
    db.run(q.result).map(_.toMap)
  }

  /** Count reactions by type for multiple publications */
  def countByPublications(pubIds: Seq[Long]): Future[Map[Long, Map[String, Int]]] = {
    if (pubIds.isEmpty) Future.successful(Map.empty)
    else {
      val q = reactions
        .filter(_.publicationId.inSet(pubIds))
        .groupBy(r => (r.publicationId, r.reactionType))
        .map { case ((pid, rt), group) => (pid, rt, group.length) }
      db.run(q.result).map { rows =>
        rows.groupBy(_._1).map { case (pid, items) =>
          pid -> items.map(i => i._2 -> i._3).toMap
        }
      }
    }
  }

  /** Get user's reactions for a publication */
  def getUserReactions(publicationId: Long, userId: Long): Future[Set[String]] = {
    val q = reactions.filter(r =>
      r.publicationId === publicationId && r.userId === userId
    ).map(_.reactionType)
    db.run(q.result).map(_.toSet)
  }

  /** Total reaction count for a user's publications */
  def totalReactionsForUser(userId: Long, pubIds: Seq[Long]): Future[Int] = {
    if (pubIds.isEmpty) Future.successful(0)
    else db.run(reactions.filter(_.publicationId.inSet(pubIds)).length.result)
  }
}
