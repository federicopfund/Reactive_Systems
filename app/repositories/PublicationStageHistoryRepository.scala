package repositories

import javax.inject.{Inject, Singleton}
import models.{PublicationStageHistory, PublicationStageHistoryWithStage}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso a la historia de transiciones de etapas de publicaciones.
 *
 * La tabla tiene una invariante crítica: para cada publicationId, exactamente
 * una fila tiene exitedAt = None. Esa invariante se mantiene por trigger de
 * base de datos (ver evolution 14), por lo que este repositorio no necesita
 * cerrar manualmente la fila anterior al insertar una nueva.
 */
@Singleton
class PublicationStageHistoryRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class StageHistoryTable(tag: Tag)
      extends Table[PublicationStageHistory](tag, "publication_stage_history") {

    def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def publicationId  = column[Long]("publication_id")
    def stageId        = column[Long]("stage_id")
    def enteredAt      = column[Instant]("entered_at")
    def exitedAt       = column[Option[Instant]]("exited_at")
    def enteredBy      = column[Option[Long]]("entered_by")
    def reason         = column[Option[String]]("reason")
    def internalNotes  = column[Option[String]]("internal_notes")

    def * = (
      id.?, publicationId, stageId, enteredAt, exitedAt,
      enteredBy, reason, internalNotes
    ).mapTo[PublicationStageHistory]
  }

  private val history = TableQuery[StageHistoryTable]

  // ─────────────────────────────────────────────────
  //  LECTURA
  // ─────────────────────────────────────────────────

  /**
   * Etapa actual de una publicación. Usa el índice parcial único
   * (publication_id) WHERE exited_at IS NULL para ser O(1).
   */
  def currentStageOf(publicationId: Long): Future[Option[PublicationStageHistory]] =
    db.run(
      history
        .filter(h => h.publicationId === publicationId && h.exitedAt.isEmpty)
        .result
        .headOption
    )

  /** Historia completa de una publicación ordenada cronológicamente. */
  def timelineOf(publicationId: Long): Future[Seq[PublicationStageHistory]] =
    db.run(
      history
        .filter(_.publicationId === publicationId)
        .sortBy(_.enteredAt.asc)
        .result
    )

  /**
   * Timeline enriquecido con datos de etapa y usuario.
   * Usado por la vista de detalle para mostrar la progresión visible.
   */
  def timelineWithStageOf(publicationId: Long): Future[Seq[PublicationStageHistoryWithStage]] = {
    import slick.jdbc.GetResult
    implicit val gr: GetResult[PublicationStageHistoryWithStage] = GetResult { r =>
      PublicationStageHistoryWithStage(
        history = PublicationStageHistory(
          id             = Some(r.nextLong()),
          publicationId  = r.nextLong(),
          stageId        = r.nextLong(),
          enteredAt      = r.nextTimestamp().toInstant,
          exitedAt       = Option(r.nextTimestamp()).map(_.toInstant),
          enteredBy      = r.nextLongOption(),
          reason         = r.nextStringOption(),
          internalNotes  = r.nextStringOption()
        ),
        stageCode         = r.nextString(),
        stageLabel        = r.nextString(),
        enteredByUsername = r.nextStringOption()
      )
    }

    val q = sql"""
      SELECT h.id, h.publication_id, h.stage_id, h.entered_at, h.exited_at,
             h.entered_by, h.reason, h.internal_notes,
             s.code, s.label, u.username
      FROM publication_stage_history h
      JOIN editorial_stages s ON s.id = h.stage_id
      LEFT JOIN users u       ON u.id = h.entered_by
      WHERE h.publication_id = $publicationId
      ORDER BY h.entered_at ASC
    """.as[PublicationStageHistoryWithStage]

    db.run(q)
  }

  /** Contar publicaciones actualmente en una etapa. Útil para el tablero editorial. */
  def countCurrentInStage(stageId: Long): Future[Int] =
    db.run(
      history
        .filter(h => h.stageId === stageId && h.exitedAt.isEmpty)
        .length
        .result
    )

  /** Todas las publicaciones actualmente en una etapa dada. */
  def currentInStage(stageId: Long): Future[Seq[Long]] =
    db.run(
      history
        .filter(h => h.stageId === stageId && h.exitedAt.isEmpty)
        .map(_.publicationId)
        .result
    )

  // ─────────────────────────────────────────────────
  //  ESCRITURA
  // ─────────────────────────────────────────────────

  /**
   * Registra una nueva transición de etapa.
   *
   * El trigger de la base de datos cierra automáticamente la fila
   * anterior con exitedAt IS NULL para la misma publicación.
   *
   * Nota sobre sincronización con publications.current_stage_id:
   * en Sprint 1 no existe trigger que sincronice este cache.
   * El llamador de este método debe actualizar currentStageId en
   * publications si corresponde. En Sprint 2 se agrega el trigger.
   */
  def insertTransition(history: PublicationStageHistory): Future[Long] = {
    val q = this.history returning this.history.map(_.id)
    db.run(q += history)
  }

  /** Insert directo de una fila completa, útil en tests. */
  def insert(entry: PublicationStageHistory): Future[Long] =
    insertTransition(entry)
}
