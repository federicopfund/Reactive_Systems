package repositories

import javax.inject.{Inject, Singleton}
import models.{Publication, PublicationWithAuthor}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class PublicationRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  /**
   * Tabla de publicaciones para Slick.
   *
   * IMPORTANTE — Sprint 1:
   * Cuatro columnas editoriales al final del mapeo `*`:
   *   - current_stage_id          (Option[Long])
   *   - publication_type          (String, default 'article')
   *   - requires_technical_review (Boolean, default false)
   *   - season_id                 (Option[Long], nullable)
   *
   * El orden en `.mapTo[Publication]` debe coincidir EXACTAMENTE
   * con el orden de los parámetros del case class Publication.
   */
  private class PublicationsTable(tag: Tag) extends Table[Publication](tag, "publications") {
    def id                      = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId                  = column[Long]("user_id")
    def title                   = column[String]("title")
    def slug                    = column[String]("slug")
    def content                 = column[String]("content")
    def excerpt                 = column[Option[String]]("excerpt")
    def coverImage              = column[Option[String]]("cover_image")
    def category                = column[String]("category")
    def tags                    = column[Option[String]]("tags")
    def status                  = column[String]("status")
    def viewCount               = column[Int]("view_count")
    def createdAt               = column[Instant]("created_at")
    def updatedAt               = column[Instant]("updated_at")
    def publishedAt             = column[Option[Instant]]("published_at")
    def reviewedBy              = column[Option[Long]]("reviewed_by")
    def reviewedAt              = column[Option[Instant]]("reviewed_at")
    def rejectionReason         = column[Option[String]]("rejection_reason")
    def adminNotes              = column[Option[String]]("admin_notes")
    // ── Columnas editoriales (Sprint 1) ──
    def currentStageId          = column[Option[Long]]("current_stage_id")
    def publicationType         = column[String]("publication_type")
    def requiresTechnicalReview = column[Boolean]("requires_technical_review")
    def seasonId                = column[Option[Long]]("season_id")

    def * = (
      id.?,
      userId,
      title,
      slug,
      content,
      excerpt,
      coverImage,
      category,
      tags,
      status,
      viewCount,
      createdAt,
      updatedAt,
      publishedAt,
      reviewedBy,
      reviewedAt,
      rejectionReason,
      adminNotes,
      // ── Editoriales ──
      currentStageId,
      publicationType,
      requiresTechnicalReview,
      seasonId
    ).mapTo[Publication]
  }

  private val publications = TableQuery[PublicationsTable]

  // Implicit GetResult para mapear JOIN con usuarios.
  // Lee las columnas en el mismo orden que `SELECT p.*, u.username, u.full_name`.
  // Las cuatro columnas editoriales se leen al final del bloque de publications,
  // antes de las columnas del JOIN con users.
  import slick.jdbc.GetResult
  implicit val getPublicationWithAuthorResult: GetResult[PublicationWithAuthor] = GetResult { r =>
    PublicationWithAuthor(
      publication = Publication(
        id                      = Some(r.nextLong()),
        userId                  = r.nextLong(),
        title                   = r.nextString(),
        slug                    = r.nextString(),
        content                 = r.nextString(),
        excerpt                 = r.nextStringOption(),
        coverImage              = r.nextStringOption(),
        category                = r.nextString(),
        tags                    = r.nextStringOption(),
        status                  = r.nextString(),
        viewCount               = r.nextInt(),
        createdAt               = r.nextTimestamp().toInstant,
        updatedAt               = r.nextTimestamp().toInstant,
        publishedAt             = r.nextTimestampOption().map(_.toInstant),
        reviewedBy              = r.nextLongOption(),
        reviewedAt              = r.nextTimestampOption().map(_.toInstant),
        rejectionReason         = r.nextStringOption(),
        adminNotes              = r.nextStringOption(),
        // ── Editoriales (Sprint 1) ──
        currentStageId          = r.nextLongOption(),
        publicationType         = r.nextString(),
        requiresTechnicalReview = r.nextBoolean(),
        seasonId                = r.nextLongOption()
      ),
      authorUsername = r.nextString(),
      authorFullName = r.nextString()
    )
  }

  // ═══════════════════════════════════════════════
  //  CREATE
  // ═══════════════════════════════════════════════

  /** Crear una nueva publicación */
  def create(publication: Publication): Future[Long] = {
    val insertQuery = publications returning publications.map(_.id)
    db.run(insertQuery += publication)
  }

  // ═══════════════════════════════════════════════
  //  UPDATE
  // ═══════════════════════════════════════════════

  /**
   * Actualizar una publicación desde el formulario de edición del autor.
   * Mantiene el mismo alcance que la versión anterior: solo campos
   * editables por el autor. Los campos editoriales (currentStageId,
   * publicationType, requiresTechnicalReview) se modifican por APIs
   * específicas, no desde este método.
   */
  def update(publication: Publication): Future[Boolean] = {
    val query = publications
      .filter(p => p.id === publication.id && p.userId === publication.userId)
      .map(p => (p.title, p.slug, p.content, p.excerpt, p.coverImage, p.category, p.tags, p.status, p.updatedAt))
      .update((
        publication.title,
        publication.slug,
        publication.content,
        publication.excerpt,
        publication.coverImage,
        publication.category,
        publication.tags,
        publication.status,
        Instant.now()
      ))

    db.run(query).map(_ > 0)
  }

  // ═══════════════════════════════════════════════
  //  READ
  // ═══════════════════════════════════════════════

  /** Obtener publicación por ID */
  def findById(id: Long): Future[Option[Publication]] = {
    db.run(publications.filter(_.id === id).result.headOption)
  }

  /** Obtener publicación por slug */
  def findBySlug(slug: String): Future[Option[Publication]] = {
    db.run(
      publications
        .filter(p => p.slug === slug && p.status === "approved")
        .result
        .headOption
    )
  }

  /** Listar publicaciones de un usuario */
  def findByUserId(userId: Long): Future[List[Publication]] = {
    db.run(
      publications
        .filter(_.userId === userId)
        .sortBy(_.createdAt.desc)
        .result
    ).map(_.toList)
  }

  /** Listar todas las publicaciones aprobadas (públicas) */
  def findAllApproved(limit: Int = 50, offset: Int = 0): Future[List[PublicationWithAuthor]] = {
    val query = sql"""
      SELECT p.*, u.username, u.full_name
      FROM publications p
      JOIN users u ON p.user_id = u.id
      WHERE p.status = 'approved'
      ORDER BY p.published_at DESC
      LIMIT $limit OFFSET $offset
    """.as[PublicationWithAuthor]

    db.run(query).map(_.toList)
  }

  /** Listar publicaciones pendientes de aprobación */
  def findPending(limit: Int = 100): Future[List[PublicationWithAuthor]] = {
    val query = sql"""
      SELECT p.*, u.username, u.full_name
      FROM publications p
      JOIN users u ON p.user_id = u.id
      WHERE p.status = 'pending'
      ORDER BY p.updated_at ASC
      LIMIT $limit
    """.as[PublicationWithAuthor]

    db.run(query).map(_.toList)
  }

  /**
   * Cambiar estado de una publicación (para admin).
   *
   * Sprint 1 — Nota importante:
   * Este método SOLO actualiza el status legado. No toca current_stage_id
   * ni crea una nueva fila en publication_stage_history. La sincronización
   * entre el mundo legado y el editorial llega en Sprint 2 vía trigger.
   *
   * En este sprint, cuando un admin aprueba/rechaza vía la UI actual:
   *   - status se actualiza aquí
   *   - current_stage_id NO se actualiza (queda desactualizado hasta que
   *     llegue el trigger o se implemente la nueva UI)
   *
   * Esto está controlado: el current_stage_id inicial fue poblado por el
   * backfill (evolution 17). Los cambios posteriores generarán drift
   * temporal hasta Sprint 2.
   */
  def changeStatus(
    publicationId: Long,
    newStatus: String,
    reviewerId: Long,
    rejectionReason: Option[String] = None
  ): Future[Boolean] = {
    val now = Instant.now()
    val publishedAt = if (newStatus == "approved") Some(now) else None

    val query = publications
      .filter(_.id === publicationId)
      .map(p => (p.status, p.reviewedBy, p.reviewedAt, p.publishedAt, p.rejectionReason))
      .update((newStatus, Some(reviewerId), Some(now), publishedAt, rejectionReason))

    db.run(query).map(_ > 0)
  }

  /** Buscar publicaciones por IDs */
  def findByIds(ids: Seq[Long]): Future[List[Publication]] = {
    if (ids.isEmpty) Future.successful(Nil)
    else db.run(publications.filter(_.id.inSet(ids)).sortBy(_.createdAt.desc).result).map(_.toList)
  }

  /** Publicaciones aprobadas de un usuario */
  def findApprovedByUserId(userId: Long): Future[List[Publication]] = {
    db.run(
      publications
        .filter(p => p.userId === userId && p.status === "approved")
        .sortBy(_.publishedAt.desc)
        .result
    ).map(_.toList)
  }

  /** Search approved publications by keyword */
  def searchApproved(query: String, category: Option[String], limit: Int = 50): Future[List[PublicationWithAuthor]] = {
    val like = s"%${query.toLowerCase}%"
    val sqlQuery = category match {
      case Some(cat) =>
        sql"""
          SELECT p.*, u.username, u.full_name
          FROM publications p
          JOIN users u ON p.user_id = u.id
          WHERE p.status = 'approved'
            AND (LOWER(p.title) LIKE $like OR LOWER(p.content) LIKE $like OR LOWER(COALESCE(p.tags,'')) LIKE $like)
            AND p.category = $cat
          ORDER BY p.published_at DESC
          LIMIT $limit
        """.as[PublicationWithAuthor]
      case None =>
        sql"""
          SELECT p.*, u.username, u.full_name
          FROM publications p
          JOIN users u ON p.user_id = u.id
          WHERE p.status = 'approved'
            AND (LOWER(p.title) LIKE $like OR LOWER(p.content) LIKE $like OR LOWER(COALESCE(p.tags,'')) LIKE $like)
          ORDER BY p.published_at DESC
          LIMIT $limit
        """.as[PublicationWithAuthor]
    }
    db.run(sqlQuery).map(_.toList)
  }

  // ═══════════════════════════════════════════════
  //  MÉTODOS EDITORIALES (Sprint 1)
  // ═══════════════════════════════════════════════

  /**
   * Actualizar los campos editoriales de una publicación.
   * Usado por herramientas administrativas y, en sprints posteriores,
   * por EditorialWorkflowEngine.
   */
  def updateEditorialFields(
    publicationId: Long,
    currentStageId: Option[Long],
    publicationType: String,
    requiresTechnicalReview: Boolean
  ): Future[Boolean] = {
    val query = publications
      .filter(_.id === publicationId)
      .map(p => (p.currentStageId, p.publicationType, p.requiresTechnicalReview))
      .update((currentStageId, publicationType, requiresTechnicalReview))

    db.run(query).map(_ > 0)
  }

  /**
   * Actualizar solo la etapa actual cacheada.
   * Se usará desde Sprint 2 para mantener la consistencia con
   * publication_stage_history.
   */
  def updateCurrentStage(publicationId: Long, stageId: Long): Future[Boolean] = {
    val query = publications
      .filter(_.id === publicationId)
      .map(_.currentStageId)
      .update(Some(stageId))

    db.run(query).map(_ > 0)
  }

  /**
   * Asignar temporada solamente si la publicación aún no tiene season_id.
   * No pisa asignaciones manuales existentes.
   */
  def assignSeasonIfEmpty(publicationId: Long, seasonIdValue: Long): Future[Boolean] = {
    val query = publications
      .filter(p => p.id === publicationId && p.seasonId.isEmpty)
      .map(_.seasonId)
      .update(Some(seasonIdValue))

    db.run(query).map(_ > 0)
  }

  /** Listar publicaciones por etapa actual. Base del tablero editorial. */
  def findByCurrentStage(stageId: Long, limit: Int = 100): Future[List[Publication]] = {
    db.run(
      publications
        .filter(_.currentStageId === stageId)
        .sortBy(_.updatedAt.desc)
        .take(limit)
        .result
    ).map(_.toList)
  }

  /** Listar publicaciones por tipo. Útil para filtros administrativos. */
  def findByType(publicationType: String, limit: Int = 100): Future[List[Publication]] = {
    db.run(
      publications
        .filter(_.publicationType === publicationType)
        .sortBy(_.createdAt.desc)
        .take(limit)
        .result
    ).map(_.toList)
  }

  // ═══════════════════════════════════════════════
  //  ADMIN
  // ═══════════════════════════════════════════════

  /** Listar publicaciones filtrando opcionalmente por status */
  def findAllByStatus(status: Option[String]): Future[List[PublicationWithAuthor]] = {
    val baseQuery = status match {
      case Some(s) =>
        sql"""
          SELECT p.*, u.username, u.full_name
          FROM publications p
          JOIN users u ON p.user_id = u.id
          WHERE p.status = $s
          ORDER BY p.updated_at DESC
        """.as[PublicationWithAuthor]
      case None =>
        sql"""
          SELECT p.*, u.username, u.full_name
          FROM publications p
          JOIN users u ON p.user_id = u.id
          ORDER BY p.updated_at DESC
        """.as[PublicationWithAuthor]
    }
    db.run(baseQuery).map(_.toList)
  }

  /** Eliminar una publicación (admin, sin restricción de userId) */
  def deleteAsAdmin(id: Long): Future[Boolean] = {
    db.run(publications.filter(_.id === id).delete).map(_ > 0)
  }

  /** Guardar notas de admin sobre una publicación */
  def saveAdminNotes(id: Long, notes: Option[String]): Future[Boolean] = {
    db.run(
      publications
        .filter(_.id === id)
        .map(_.adminNotes)
        .update(notes)
    ).map(_ > 0)
  }

  // ═══════════════════════════════════════════════
  //  USER
  // ═══════════════════════════════════════════════

  /** Incrementar el contador de vistas */
  def incrementViewCount(id: Long): Future[Unit] = {
    db.run(
      sqlu"UPDATE publications SET view_count = view_count + 1 WHERE id = $id"
    ).map(_ => ())
  }

  /** Obtener estadísticas del usuario */
  def getUserStats(userId: Long): Future[Map[String, Int]] = {
    db.run(
      publications.filter(_.userId === userId).result
    ).map { pubs =>
      Map(
        "total"    -> pubs.size,
        "approved" -> pubs.count(_.status == "approved"),
        "pending"  -> pubs.count(_.status == "pending"),
        "rejected" -> pubs.count(_.status == "rejected"),
        "draft"    -> pubs.count(_.status == "draft"),
        "views"    -> pubs.map(_.viewCount).sum
      )
    }
  }

  /** Eliminar publicación de un usuario (solo si le pertenece) */
  def delete(id: Long, userId: Long): Future[Boolean] = {
    db.run(
      publications.filter(p => p.id === id && p.userId === userId).delete
    ).map(_ > 0)
  }
}
