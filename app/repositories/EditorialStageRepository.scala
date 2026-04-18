package repositories

import javax.inject.{Inject, Singleton}
import models.EditorialStage
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso al catálogo de etapas editoriales.
 *
 * Este catálogo cambia raramente (solo cuando se agregan o desactivan
 * etapas del flujo), por lo que las implementaciones pueden cachear
 * en memoria los resultados.
 */
@Singleton
class EditorialStageRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class EditorialStagesTable(tag: Tag)
      extends Table[EditorialStage](tag, "editorial_stages") {

    def id               = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def code             = column[String]("code")
    def label            = column[String]("label")
    def description      = column[Option[String]]("description")
    def orderIndex       = column[Int]("order_index")
    def isTerminal       = column[Boolean]("is_terminal")
    def requiredRole     = column[Option[String]]("required_role")
    def allowsAuthorEdit = column[Boolean]("allows_author_edit")
    def active           = column[Boolean]("active")
    def createdAt        = column[Instant]("created_at")

    def * = (
      id.?, code, label, description, orderIndex,
      isTerminal, requiredRole, allowsAuthorEdit, active, createdAt
    ).mapTo[EditorialStage]
  }

  private val stages = TableQuery[EditorialStagesTable]

  /** Todas las etapas ordenadas canónicamente. */
  def findAll(): Future[Seq[EditorialStage]] =
    db.run(stages.sortBy(_.orderIndex.asc).result)

  /** Etapas activas (para selectores de UI). */
  def findActive(): Future[Seq[EditorialStage]] =
    db.run(stages.filter(_.active).sortBy(_.orderIndex.asc).result)

  /** Buscar por código canónico. */
  def findByCode(code: String): Future[Option[EditorialStage]] =
    db.run(stages.filter(_.code === code).result.headOption)

  /** Buscar por PK. */
  def findById(id: Long): Future[Option[EditorialStage]] =
    db.run(stages.filter(_.id === id).result.headOption)

  /**
   * Resolver el id numérico desde el código, útil en inserts.
   * Lanza NoSuchElementException si el código no existe.
   */
  def idOf(code: String): Future[Long] =
    findByCode(code).map {
      case Some(s) => s.id.get
      case None    => throw new NoSuchElementException(s"Editorial stage not found: $code")
    }
}
