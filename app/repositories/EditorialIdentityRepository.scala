package repositories

import javax.inject.{Inject, Singleton}
import models.EditorialIdentity
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Bloques de identidad editorial (Issue #21). Patrón: tabla pequeña
 * editable desde backoffice, sin deploy.
 */
@Singleton
class EditorialIdentityRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Identities(tag: Tag)
      extends Table[EditorialIdentity](tag, "editorial_identity") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def sectionKey = column[String]("section_key")
    def title      = column[String]("title")
    def bodyHtml   = column[String]("body_html")
    def orderIndex = column[Int]("order_index")
    def active     = column[Boolean]("active")
    def updatedAt  = column[Instant]("updated_at")

    def * = (
      id.?, sectionKey, title, bodyHtml, orderIndex, active, updatedAt
    ).mapTo[EditorialIdentity]
  }

  private val identities = TableQuery[Identities]

  /** Bloques activos en orden de presentación. */
  def findActive(): Future[Seq[EditorialIdentity]] =
    db.run(identities.filter(_.active).sortBy(_.orderIndex.asc).result)

  /** Todos los bloques (incluye inactivos) — para el backoffice. */
  def findAll(): Future[Seq[EditorialIdentity]] =
    db.run(identities.sortBy(_.orderIndex.asc).result)

  def findBySectionKey(key: String): Future[Option[EditorialIdentity]] =
    db.run(identities.filter(_.sectionKey === key).result.headOption)

  /** Update de los campos editables desde backoffice (no se cambia section_key). */
  def updateContent(
    sectionKey: String,
    title:      String,
    bodyHtml:   String,
    orderIndex: Int,
    active:     Boolean
  ): Future[Int] = {
    val q = identities.filter(_.sectionKey === sectionKey)
      .map(r => (r.title, r.bodyHtml, r.orderIndex, r.active, r.updatedAt))
    db.run(q.update((title, bodyHtml, orderIndex, active, Instant.now())))
  }
}
