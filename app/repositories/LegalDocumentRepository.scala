package repositories

import javax.inject.{Inject, Singleton}
import models.LegalDocument
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Documentos legales (privacidad, términos). Servidos por slug.
 */
@Singleton
class LegalDocumentRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Docs(tag: Tag)
      extends Table[LegalDocument](tag, "legal_documents") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug          = column[String]("slug")
    def title         = column[String]("title")
    def eyebrow       = column[String]("eyebrow")
    def bodyHtml      = column[String]("body_html")
    def lastUpdatedAt = column[Instant]("last_updated_at")
    def isPublished   = column[Boolean]("is_published")
    def createdAt     = column[Instant]("created_at")

    def * = (
      id.?, slug, title, eyebrow, bodyHtml, lastUpdatedAt, isPublished, createdAt
    ).mapTo[LegalDocument]
  }

  private val docs = TableQuery[Docs]

  /** Documento publicado por slug. None si no existe o está despublicado. */
  def findPublishedBySlug(slug: String): Future[Option[LegalDocument]] =
    db.run(docs.filter(d => d.slug === slug && d.isPublished).result.headOption)
}
