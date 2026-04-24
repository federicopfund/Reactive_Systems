package repositories

import javax.inject.{Inject, Singleton}
import models.PublicationRevision
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso a versiones del contenido de publicaciones.
 *
 * Cada pieza tiene al menos una revisión (v1, creada al ingresar al
 * sistema o retroactivamente para las piezas anteriores a Sprint 1
 * vía evolution 19). Cuando el autor entrega cambios tras feedback,
 * se crea una nueva versión con número incrementado.
 */
@Singleton
class PublicationRevisionRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class RevisionsTable(tag: Tag)
      extends Table[PublicationRevision](tag, "publication_revisions") {

    def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def publicationId  = column[Long]("publication_id")
    def versionNumber  = column[Int]("version_number")
    def title          = column[String]("title")
    def content        = column[String]("content")
    def excerpt        = column[Option[String]]("excerpt")
    def createdAt      = column[Instant]("created_at")
    def createdBy      = column[Option[Long]]("created_by")
    def changeSummary  = column[Option[String]]("change_summary")

    def * = (
      id.?, publicationId, versionNumber, title, content, excerpt,
      createdAt, createdBy, changeSummary
    ).mapTo[PublicationRevision]
  }

  private val revisions = TableQuery[RevisionsTable]

  // ═══════════════════════════════════════════════
  //  LECTURA
  // ═══════════════════════════════════════════════

  /** Todas las revisiones de una publicación, de la más nueva a la más vieja. */
  def findByPublication(publicationId: Long): Future[Seq[PublicationRevision]] =
    db.run(
      revisions
        .filter(_.publicationId === publicationId)
        .sortBy(_.versionNumber.desc)
        .result
    )

  /** Revisión específica por número de versión. */
  def findVersion(publicationId: Long, versionNumber: Int): Future[Option[PublicationRevision]] =
    db.run(
      revisions
        .filter(r => r.publicationId === publicationId && r.versionNumber === versionNumber)
        .result
        .headOption
    )

  /** Última revisión de una publicación. */
  def latestVersion(publicationId: Long): Future[Option[PublicationRevision]] =
    db.run(
      revisions
        .filter(_.publicationId === publicationId)
        .sortBy(_.versionNumber.desc)
        .result
        .headOption
    )

  /** Número máximo de versión alcanzado para una publicación (0 si ninguna). */
  def maxVersionNumber(publicationId: Long): Future[Int] =
    db.run(
      revisions
        .filter(_.publicationId === publicationId)
        .map(_.versionNumber)
        .max
        .result
    ).map(_.getOrElse(0))

  /** Contar revisiones totales (diagnóstico). */
  def countAll(): Future[Int] =
    db.run(revisions.length.result)

  /** Buscar revisión por id directo. */
  def findById(revisionId: Long): Future[Option[PublicationRevision]] =
    db.run(revisions.filter(_.id === revisionId).result.headOption)

  // ═══════════════════════════════════════════════
  //  ESCRITURA
  // ═══════════════════════════════════════════════

  /**
   * Crea una nueva revisión con versionNumber explícito.
   *
   * El llamador es responsable de setear versionNumber correctamente
   * (típicamente maxVersionNumber + 1). El constraint único de BD
   * garantiza que no haya colisión si dos requests intentan crear
   * la misma versión en paralelo: el segundo falla con violación
   * de constraint.
   */
  def create(revision: PublicationRevision): Future[Long] = {
    val q = revisions returning revisions.map(_.id)
    db.run(q += revision)
  }

  /**
   * Helper: lee el último versionNumber y crea la siguiente versión.
   *
   * ATENCIÓN (Sprint 1):
   * Este método hace dos queries (SELECT max + INSERT) sin transacción
   * explícita. Bajo concurrencia alta, dos requests podrían leer el
   * mismo max y el segundo INSERT fallaría por UNIQUE constraint.
   *
   * En Sprint 1 no es crítico porque nadie crea revisiones desde la UI
   * todavía (solo el backfill, que corre una única vez). A partir de
   * Sprint 4, cuando el editor empiece a generar versiones, considerar
   * envolver en una transacción con nivel SERIALIZABLE o usar un
   * advisory lock de PostgreSQL.
   */
  def createNextVersion(
    publicationId: Long,
    title: String,
    content: String,
    excerpt: Option[String],
    createdBy: Option[Long],
    changeSummary: Option[String]
  ): Future[PublicationRevision] = {
    for {
      max <- maxVersionNumber(publicationId)
      rev = PublicationRevision(
        publicationId = publicationId,
        versionNumber = max + 1,
        title         = title,
        content       = content,
        excerpt       = excerpt,
        createdBy     = createdBy,
        changeSummary = changeSummary
      )
      id  <- create(rev)
    } yield rev.copy(id = Some(id))
  }
}
