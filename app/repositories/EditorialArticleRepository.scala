package repositories

import javax.inject.{Inject, Singleton}
import models.EditorialArticle
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso a editorial_articles: piezas fundacionales del equipo de la edición.
 *
 * Convive con PublicationRepository: el listado público muestra ambos
 * tipos y la resolución por slug se hace en cascada.
 */
@Singleton
class EditorialArticleRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Articles(tag: Tag)
      extends Table[EditorialArticle](tag, "editorial_articles") {
    def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug           = column[String]("slug")
    def title          = column[String]("title")
    def excerpt        = column[Option[String]]("excerpt")
    def bodyHtml       = column[String]("body_html")
    def categoryId     = column[Option[Long]]("category_id")
    def categoryLabel  = column[String]("category_label")
    def tagsPipe       = column[String]("tags_pipe")
    def publishedLabel = column[String]("published_label")
    def publishedAt    = column[Instant]("published_at")
    def coverImage     = column[Option[String]]("cover_image")
    def isPublished    = column[Boolean]("is_published")
    def viewCount      = column[Int]("view_count")
    def orderIndex     = column[Int]("order_index")
    def createdAt      = column[Instant]("created_at")
    def updatedAt      = column[Instant]("updated_at")

    def * = (
      id.?, slug, title, excerpt, bodyHtml, categoryId, categoryLabel,
      tagsPipe, publishedLabel, publishedAt, coverImage,
      isPublished, viewCount, orderIndex, createdAt, updatedAt
    ).mapTo[EditorialArticle]
  }

  private val articles = TableQuery[Articles]

  /** Listado público: solo publicadas, en orden canónico. */
  def findAllPublished(limit: Int = 50): Future[Seq[EditorialArticle]] =
    db.run(
      articles.filter(_.isPublished)
        .sortBy(a => (a.orderIndex.asc, a.publishedAt.desc))
        .take(limit)
        .result
    )

  /** Resolver una pieza por slug (incluye no publicadas para preview futuro). */
  def findBySlug(slug: String): Future[Option[EditorialArticle]] =
    db.run(articles.filter(_.slug === slug).result.headOption)

  /** Filtrar por categoría (slug del catálogo). Une por categoryId. */
  def findPublishedByCategorySlug(categorySlug: String): Future[Seq[EditorialArticle]] = {
    val q = for {
      a <- articles if a.isPublished
      c <- TableQuery[CategoriesShim] if a.categoryId === c.id && c.slug === categorySlug
    } yield a
    db.run(q.sortBy(a => (a.orderIndex.asc, a.publishedAt.desc)).result)
  }

  /** Búsqueda libre + opcionalmente filtrada por categoría (slug). */
  def search(q: String, categorySlug: Option[String]): Future[Seq[EditorialArticle]] = {
    val pattern = s"%${q.trim.toLowerCase}%"
    val base = articles.filter(_.isPublished)
    val withText =
      if (q.trim.isEmpty) base
      else base.filter(a =>
        a.title.toLowerCase.like(pattern) ||
        a.bodyHtml.toLowerCase.like(pattern) ||
        a.tagsPipe.toLowerCase.like(pattern)
      )
    val finalQ = categorySlug match {
      case Some(slug) =>
        for {
          a <- withText
          c <- TableQuery[CategoriesShim] if a.categoryId === c.id && c.slug === slug
        } yield a
      case None => withText
    }
    db.run(finalQ.sortBy(a => (a.orderIndex.asc, a.publishedAt.desc)).result)
  }

  /** Incrementa el contador de vistas (fire-and-forget). */
  def incrementViewCount(id: Long): Future[Int] =
    db.run(articles.filter(_.id === id).map(_.viewCount).result.headOption.flatMap {
      case Some(current) => articles.filter(_.id === id).map(_.viewCount).update(current + 1)
      case None          => DBIO.successful(0)
    }.transactionally)

  // ── Tabla shim: solo necesitamos slug+id de publication_categories
  // para los joins arriba sin acoplar este repo al CategoryRepository.
  private class CategoriesShim(tag: Tag)
      extends Table[(Long, String)](tag, "publication_categories") {
    def id   = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug = column[String]("slug")
    def *    = (id, slug)
  }
}
