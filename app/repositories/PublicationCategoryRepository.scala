package repositories

import javax.inject.{Inject, Singleton}
import models.PublicationCategory
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Catálogo de categorías de publicaciones.
 * Cambia raramente — es seguro cachear en memoria desde la app.
 */
@Singleton
class PublicationCategoryRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Categories(tag: Tag)
      extends Table[PublicationCategory](tag, "publication_categories") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug        = column[String]("slug")
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def iconEmoji   = column[Option[String]]("icon_emoji")
    def orderIndex  = column[Int]("order_index")
    def active      = column[Boolean]("active")
    def createdAt   = column[Instant]("created_at")

    def * = (
      id.?, slug, name, description, iconEmoji, orderIndex, active, createdAt
    ).mapTo[PublicationCategory]
  }

  private val categories = TableQuery[Categories]

  /** Catálogo completo ordenado canónicamente. */
  def findAll(): Future[Seq[PublicationCategory]] =
    db.run(categories.sortBy(_.orderIndex.asc).result)

  /** Solo categorías activas (para selectores y filtros públicos). */
  def findActive(): Future[Seq[PublicationCategory]] =
    db.run(categories.filter(_.active).sortBy(_.orderIndex.asc).result)

  def findBySlug(slug: String): Future[Option[PublicationCategory]] =
    db.run(categories.filter(_.slug === slug).result.headOption)

  def findByName(name: String): Future[Option[PublicationCategory]] =
    db.run(categories.filter(_.name.toLowerCase === name.toLowerCase).result.headOption)

  def findById(id: Long): Future[Option[PublicationCategory]] =
    db.run(categories.filter(_.id === id).result.headOption)
}
