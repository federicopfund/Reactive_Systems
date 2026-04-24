package repositories

import javax.inject.{Inject, Singleton}
import models.{PublicationFeedback, PublicationFeedbackWithAdmin, FeedbackVisibility}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso a feedback editorial sobre publicaciones.
 *
 * COMPATIBILIDAD — Sprint 1:
 * Los 7 métodos públicos originales se conservan con misma firma y
 * comportamiento (create, findByPublicationId, findVisibleByPublicationId,
 * markAsSent, countVisibleByPublicationId, countVisibleByPublicationIds,
 * delete). Código que los invoca desde AdminController sigue funcionando
 * sin cambios.
 *
 * NUEVO — Sprint 1:
 * Siete campos editoriales agregados (revision_id, parent_id,
 * anchor_selector, anchor_text, resolved_at, resolved_by, visibility).
 * Nuevos métodos: findThread, resolveFeedback, findByRevision,
 * findAnchored, findUnresolved, countUnresolved.
 *
 * NOTA SOBRE sent_to_user vs visibility:
 * Los writes por los métodos legados (create, markAsSent) mantienen
 * sent_to_user sincronizado con visibility automáticamente vía DEFAULT
 * y triggers del SQL. Los nuevos writes usan visibility directamente.
 */
@Singleton
class PublicationFeedbackRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class FeedbackTable(tag: Tag) extends Table[PublicationFeedback](tag, "publication_feedback") {
    def id              = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def publicationId   = column[Long]("publication_id")
    def adminId         = column[Long]("admin_id")
    def feedbackType    = column[String]("feedback_type")
    def message         = column[String]("message")
    def sentToUser      = column[Boolean]("sent_to_user")
    def createdAt       = column[Instant]("created_at")
    // ── Campos editoriales (Sprint 1) ──
    def revisionId      = column[Option[Long]]("revision_id")
    def parentId        = column[Option[Long]]("parent_id")
    def anchorSelector  = column[Option[String]]("anchor_selector")
    def anchorText      = column[Option[String]]("anchor_text")
    def resolvedAt      = column[Option[Instant]]("resolved_at")
    def resolvedBy      = column[Option[Long]]("resolved_by")
    def visibility      = column[String]("visibility")

    def * = (
      id.?, publicationId, adminId, feedbackType, message, sentToUser, createdAt,
      // ── Editoriales ──
      revisionId, parentId, anchorSelector, anchorText, resolvedAt, resolvedBy, visibility
    ).mapTo[PublicationFeedback]
  }

  private val feedbacks = TableQuery[FeedbackTable]

  // GetResult compartido para las queries con SQL crudo.
  // Lee las 14 columnas del feedback en el orden del tabla + el username del JOIN.
  import slick.jdbc.GetResult
  private implicit val feedbackWithAdminResult: GetResult[PublicationFeedbackWithAdmin] =
    GetResult { r =>
      PublicationFeedbackWithAdmin(
        feedback = PublicationFeedback(
          id              = Some(r.nextLong()),
          publicationId   = r.nextLong(),
          adminId         = r.nextLong(),
          feedbackType    = r.nextString(),
          message         = r.nextString(),
          sentToUser      = r.nextBoolean(),
          createdAt       = r.nextTimestamp().toInstant,
          revisionId      = r.nextLongOption(),
          parentId        = r.nextLongOption(),
          anchorSelector  = r.nextStringOption(),
          anchorText      = r.nextStringOption(),
          resolvedAt      = r.nextTimestampOption().map(_.toInstant),
          resolvedBy      = r.nextLongOption(),
          visibility      = r.nextString()
        ),
        adminUsername = r.nextString()
      )
    }

  private val selectAllColumns = """
    SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
           f.sent_to_user, f.created_at,
           f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
           f.resolved_at, f.resolved_by, f.visibility,
           u.username
    FROM publication_feedback f
    JOIN users u ON f.admin_id = u.id
  """

  // ═══════════════════════════════════════════════
  //  API LEGADA (preservada para compatibilidad)
  // ═══════════════════════════════════════════════

  /** Crear un nuevo feedback */
  def create(feedback: PublicationFeedback): Future[Long] = {
    val insertQuery = feedbacks returning feedbacks.map(_.id)
    db.run(insertQuery += feedback)
  }

  /** Obtener todos los feedbacks de una publicación (para admin) */
  def findByPublicationId(publicationId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    val query = sql"""
      SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
             f.sent_to_user, f.created_at,
             f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
             f.resolved_at, f.resolved_by, f.visibility,
             u.username
      FROM publication_feedback f
      JOIN users u ON f.admin_id = u.id
      WHERE f.publication_id = $publicationId
      ORDER BY f.created_at DESC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /** Obtener solo feedbacks enviados al usuario (sent_to_user = true) */
  def findVisibleByPublicationId(publicationId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    val query = sql"""
      SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
             f.sent_to_user, f.created_at,
             f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
             f.resolved_at, f.resolved_by, f.visibility,
             u.username
      FROM publication_feedback f
      JOIN users u ON f.admin_id = u.id
      WHERE f.publication_id = $publicationId AND f.sent_to_user = true
      ORDER BY f.created_at DESC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /**
   * Marcar un feedback como enviado al usuario.
   * Sincroniza sent_to_user=true con visibility='both'.
   */
  def markAsSent(feedbackId: Long): Future[Boolean] = {
    val query = feedbacks
      .filter(_.id === feedbackId)
      .map(f => (f.sentToUser, f.visibility))
      .update((true, FeedbackVisibility.Both))
    db.run(query).map(_ > 0)
  }

  /** Contar feedbacks visibles para una publicación */
  def countVisibleByPublicationId(publicationId: Long): Future[Int] = {
    val query = feedbacks
      .filter(f => f.publicationId === publicationId && f.sentToUser === true)
      .length
    db.run(query.result)
  }

  /** Contar feedbacks visibles para múltiples publicaciones de un usuario */
  def countVisibleByPublicationIds(pubIds: Seq[Long]): Future[Map[Long, Int]] = {
    if (pubIds.isEmpty) return Future.successful(Map.empty)

    val query = feedbacks
      .filter(f => f.publicationId.inSet(pubIds) && f.sentToUser === true)
      .groupBy(_.publicationId)
      .map { case (pubId, group) => (pubId, group.length) }

    db.run(query.result).map(_.toMap)
  }

  /** Eliminar un feedback */
  def delete(feedbackId: Long): Future[Boolean] = {
    db.run(feedbacks.filter(_.id === feedbackId).delete).map(_ > 0)
  }

  // ═══════════════════════════════════════════════
  //  API EDITORIAL (Sprint 1)
  // ═══════════════════════════════════════════════

  /** Obtener todos los feedbacks asociados a una revisión específica. */
  def findByRevision(revisionId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    val query = sql"""
      SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
             f.sent_to_user, f.created_at,
             f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
             f.resolved_at, f.resolved_by, f.visibility,
             u.username
      FROM publication_feedback f
      JOIN users u ON f.admin_id = u.id
      WHERE f.revision_id = $revisionId
      ORDER BY f.created_at ASC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /**
   * Obtener el hilo completo (un comentario raíz + todas sus respuestas
   * descendientes recursivamente). Ordenado cronológicamente.
   */
  def findThread(rootId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    // PostgreSQL CTE recursivo para navegar el árbol parent_id → id.
    val query = sql"""
      WITH RECURSIVE thread AS (
        SELECT f.*
        FROM publication_feedback f
        WHERE f.id = $rootId
        UNION ALL
        SELECT f.*
        FROM publication_feedback f
        JOIN thread t ON f.parent_id = t.id
      )
      SELECT t.id, t.publication_id, t.admin_id, t.feedback_type, t.message,
             t.sent_to_user, t.created_at,
             t.revision_id, t.parent_id, t.anchor_selector, t.anchor_text,
             t.resolved_at, t.resolved_by, t.visibility,
             u.username
      FROM thread t
      JOIN users u ON t.admin_id = u.id
      ORDER BY t.created_at ASC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /** Obtener solo feedbacks anclados a un fragmento del texto. */
  def findAnchored(publicationId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    val query = sql"""
      SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
             f.sent_to_user, f.created_at,
             f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
             f.resolved_at, f.resolved_by, f.visibility,
             u.username
      FROM publication_feedback f
      JOIN users u ON f.admin_id = u.id
      WHERE f.publication_id = $publicationId AND f.anchor_selector IS NOT NULL
      ORDER BY f.created_at ASC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /** Feedbacks no resueltos de una publicación. */
  def findUnresolved(publicationId: Long): Future[List[PublicationFeedbackWithAdmin]] = {
    val query = sql"""
      SELECT f.id, f.publication_id, f.admin_id, f.feedback_type, f.message,
             f.sent_to_user, f.created_at,
             f.revision_id, f.parent_id, f.anchor_selector, f.anchor_text,
             f.resolved_at, f.resolved_by, f.visibility,
             u.username
      FROM publication_feedback f
      JOIN users u ON f.admin_id = u.id
      WHERE f.publication_id = $publicationId AND f.resolved_at IS NULL
      ORDER BY f.created_at ASC
    """.as[PublicationFeedbackWithAdmin]

    db.run(query).map(_.toList)
  }

  /** Contar feedbacks no resueltos de una publicación. */
  def countUnresolved(publicationId: Long): Future[Int] = {
    val query = feedbacks
      .filter(f => f.publicationId === publicationId && f.resolvedAt.isEmpty)
      .length
    db.run(query.result)
  }

  /**
   * Marcar un feedback como resuelto por un usuario.
   * El autor lo marca tras incorporar el cambio; el editor lo marca
   * tras validar la resolución.
   */
  def resolveFeedback(feedbackId: Long, resolvedBy: Long): Future[Boolean] = {
    val now = Instant.now()
    val query = feedbacks
      .filter(_.id === feedbackId)
      .map(f => (f.resolvedAt, f.resolvedBy))
      .update((Some(now), Some(resolvedBy)))
    db.run(query).map(_ > 0)
  }

  /**
   * Reabrir un feedback resuelto (limpia resolved_at y resolved_by).
   * Útil si el autor marca como resuelto pero el editor detecta que
   * el cambio no resolvió la observación.
   */
  def reopenFeedback(feedbackId: Long): Future[Boolean] = {
    val query = feedbacks
      .filter(_.id === feedbackId)
      .map(f => (f.resolvedAt, f.resolvedBy))
      .update((None, None))
    db.run(query).map(_ > 0)
  }

  /**
   * Cambiar la visibilidad de un feedback.
   * Usado cuando el editor decide promover una nota interna a feedback
   * visible para el autor, o viceversa.
   */
  def setVisibility(feedbackId: Long, visibility: String): Future[Boolean] = {
    require(FeedbackVisibility.all.contains(visibility),
      s"Visibility inválida: $visibility")
    val sentToUser = FeedbackVisibility.toSentToUser(visibility)
    val query = feedbacks
      .filter(_.id === feedbackId)
      .map(f => (f.visibility, f.sentToUser))
      .update((visibility, sentToUser))
    db.run(query).map(_ > 0)
  }
}
