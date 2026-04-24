package repositories

import javax.inject.{Inject, Singleton}
import models.{
  Collection, CollectionItem, CollectionItemType, CollectionStatus,
  CollectionStatusEntry, CollectionWithCount, PickableItem, ResolvedCollectionItem
}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Acceso a colecciones temáticas curadas (Issue #20).
 *
 * Contiene tres tablas:
 *   - collections                    (cabecera + workflow + auditoría)
 *   - collection_items               (piezas curadas, con `curator_note`)
 *   - collection_status_history      (historial inmutable de transiciones)
 *
 * Toda transición de estado se registra atómicamente en una transacción
 * que escribe en `collections` + `collection_status_history`.
 */
@Singleton
class CollectionRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  // ──────────────────────── Tablas Slick ────────────────────────

  private class Collections(tag: Tag)
      extends Table[Collection](tag, "collections") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def slug         = column[String]("slug")
    def name         = column[String]("name")
    def description  = column[Option[String]]("description")
    def coverLabel   = column[String]("cover_label")
    def curatorId    = column[Option[Long]]("curator_id")
    def isPublished  = column[Boolean]("is_published")
    def orderIndex   = column[Int]("order_index")
    def createdAt    = column[Instant]("created_at")
    def updatedAt    = column[Instant]("updated_at")
    def status       = column[String]("status")
    def createdBy    = column[Option[Long]]("created_by")
    def submittedAt  = column[Option[Instant]]("submitted_at")
    def reviewedBy   = column[Option[Long]]("reviewed_by")
    def reviewedAt   = column[Option[Instant]]("reviewed_at")
    def publishedBy  = column[Option[Long]]("published_by")
    def publishedAt  = column[Option[Instant]]("published_at")
    def reviewNotes  = column[Option[String]]("review_notes")
    def accentColor  = column[Option[String]]("accent_color")

    def * = (
      id.?, slug, name, description, coverLabel, curatorId,
      isPublished, orderIndex, createdAt, updatedAt,
      status, createdBy, submittedAt, reviewedBy, reviewedAt,
      publishedBy, publishedAt, reviewNotes, accentColor
    ).mapTo[Collection]
  }

  private class CollectionItems(tag: Tag)
      extends Table[CollectionItem](tag, "collection_items") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def collectionId = column[Long]("collection_id")
    def itemType     = column[String]("item_type")
    def itemId       = column[Long]("item_id")
    def orderIndex   = column[Int]("order_index")
    def addedAt      = column[Instant]("added_at")
    def curatorNote  = column[Option[String]]("curator_note")

    def * = (
      id.?, collectionId, itemType, itemId, orderIndex, addedAt, curatorNote
    ).mapTo[CollectionItem]
  }

  private class StatusHistory(tag: Tag)
      extends Table[CollectionStatusEntry](tag, "collection_status_history") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def collectionId = column[Long]("collection_id")
    def fromStatus   = column[Option[String]]("from_status")
    def toStatus     = column[String]("to_status")
    def actorId      = column[Option[Long]]("actor_id")
    def actorRole    = column[Option[String]]("actor_role")
    def comment      = column[Option[String]]("comment")
    def createdAt    = column[Instant]("created_at")

    def * = (
      id.?, collectionId, fromStatus, toStatus, actorId, actorRole, comment, createdAt
    ).mapTo[CollectionStatusEntry]
  }

  private val collections = TableQuery[Collections]
  private val items       = TableQuery[CollectionItems]
  private val history     = TableQuery[StatusHistory]

  // ──────────────────────── Lecturas ────────────────────────

  def findById(id: Long): Future[Option[Collection]] =
    db.run(collections.filter(_.id === id).result.headOption)

  def findBySlug(slug: String): Future[Option[Collection]] =
    db.run(collections.filter(_.slug === slug).result.headOption)

  def slugExists(slug: String, excludeId: Option[Long] = None): Future[Boolean] = {
    val q = excludeId match {
      case Some(id) => collections.filter(c => c.slug === slug && c.id =!= id)
      case None     => collections.filter(_.slug === slug)
    }
    db.run(q.exists.result)
  }

  def findItems(collectionId: Long): Future[Seq[CollectionItem]] =
    db.run(items.filter(_.collectionId === collectionId).sortBy(_.orderIndex.asc).result)

  /** Listado para el backoffice. `statusFilter=None` => todos. */
  def findAllForAdmin(statusFilter: Option[String]): Future[Seq[CollectionWithCount]] = {
    val base = statusFilter.filter(CollectionStatus.isValid) match {
      case Some(s) => collections.filter(_.status === s)
      case None    => collections
    }
    val q = base
      .joinLeft(items).on(_.id === _.collectionId)
      .result
    db.run(q).map { rows =>
      rows.groupBy(_._1.id).toSeq.map { case (_, group) =>
        val coll  = group.head._1
        val count = group.count(_._2.isDefined)
        CollectionWithCount(coll, count)
      }.sortBy(cw => (cw.collection.status, -cw.collection.updatedAt.toEpochMilli))
    }
  }

  /** Colecciones publicadas con conteo de piezas, en orden canónico. */
  def findPublishedWithCounts(): Future[Seq[CollectionWithCount]] = {
    val q = collections
      .filter(c => c.isPublished && c.status === CollectionStatus.Published)
      .joinLeft(items).on(_.id === _.collectionId)
      .result

    db.run(q).map { rows =>
      rows.groupBy(_._1.id).toSeq.map { case (_, group) =>
        val coll  = group.head._1
        val count = group.count(_._2.isDefined)
        CollectionWithCount(coll, count)
      }.sortBy(_.collection.orderIndex)
    }
  }

  // ──────────────────────── Escrituras ────────────────────────

  /**
   * Crea una colección en estado `draft` y registra la entrada inicial en el historial.
   * Devuelve el id generado.
   */
  def create(c: Collection, actorId: Long, actorRole: String): Future[Long] = {
    val now = Instant.now()
    val toInsert = c.copy(
      status      = CollectionStatus.Draft,
      isPublished = false,
      createdBy   = Some(actorId),
      curatorId   = c.curatorId.orElse(Some(actorId)),
      createdAt   = now,
      updatedAt   = now
    )
    val action = (for {
      newId <- (collections returning collections.map(_.id)) += toInsert
      _     <- history += CollectionStatusEntry(
                 collectionId = newId,
                 fromStatus   = None,
                 toStatus     = CollectionStatus.Draft,
                 actorId      = Some(actorId),
                 actorRole    = Some(actorRole),
                 comment      = Some("Colección creada"),
                 createdAt    = now
               )
    } yield newId).transactionally
    db.run(action)
  }

  /**
   * Actualización de metadatos (solo cuando isEditable: draft o rejected).
   * No toca el estado ni el historial.
   */
  def updateMeta(
    id: Long, name: String, slug: String, description: Option[String],
    coverLabel: String, accentColor: Option[String], curatorId: Option[Long],
    orderIndex: Int
  ): Future[Int] = {
    val now = Instant.now()
    val q = collections.filter(_.id === id)
      .map(c => (c.name, c.slug, c.description, c.coverLabel, c.accentColor, c.curatorId, c.orderIndex, c.updatedAt))
      .update((name, slug, description, coverLabel, accentColor, curatorId, orderIndex, now))
    db.run(q)
  }

  /**
   * Aplica una transición de estado de forma atómica: actualiza la cabecera,
   * sincroniza `is_published` y registra una entrada en el historial.
   *
   * Devuelve `Some(newCollection)` si la transición se aplicó, `None` si la
   * colección no existe o el `from` no coincide con el estado actual (lock optimista).
   */
  def transition(
    id: Long, expectedFrom: String, to: String,
    actorId: Long, actorRole: String, comment: Option[String]
  ): Future[Option[Collection]] = {
    val now = Instant.now()
    val syncPublished = to == CollectionStatus.Published

    val updates: DBIO[Int] = to match {
      case CollectionStatus.InReview =>
        collections.filter(c => c.id === id && c.status === expectedFrom)
          .map(c => (c.status, c.submittedAt, c.updatedAt))
          .update((to, Some(now), now))
      case CollectionStatus.Approved =>
        collections.filter(c => c.id === id && c.status === expectedFrom)
          .map(c => (c.status, c.reviewedBy, c.reviewedAt, c.reviewNotes, c.updatedAt))
          .update((to, Some(actorId), Some(now), comment, now))
      case CollectionStatus.Rejected =>
        collections.filter(c => c.id === id && c.status === expectedFrom)
          .map(c => (c.status, c.reviewedBy, c.reviewedAt, c.reviewNotes, c.updatedAt))
          .update((to, Some(actorId), Some(now), comment, now))
      case CollectionStatus.Published =>
        collections.filter(c => c.id === id && c.status === expectedFrom)
          .map(c => (c.status, c.publishedBy, c.publishedAt, c.isPublished, c.updatedAt))
          .update((to, Some(actorId), Some(now), true, now))
      case _ =>
        collections.filter(c => c.id === id && c.status === expectedFrom)
          .map(c => (c.status, c.isPublished, c.updatedAt))
          .update((to, syncPublished, now))
    }

    val action = (for {
      n <- updates
      _ <- if (n == 1)
             history += CollectionStatusEntry(
               collectionId = id,
               fromStatus   = Some(expectedFrom),
               toStatus     = to,
               actorId      = Some(actorId),
               actorRole    = Some(actorRole),
               comment      = comment,
               createdAt    = now
             )
           else DBIO.successful(0)
      result <- if (n == 1) collections.filter(_.id === id).result.headOption
                else DBIO.successful(Option.empty[Collection])
    } yield result).transactionally

    db.run(action)
  }

  // ──────────────────────── Items ────────────────────────

  /** Inserta un item al final de la colección (orderIndex = max+10). */
  def addItem(collectionId: Long, itemType: String, itemId: Long, note: Option[String]): Future[Long] = {
    val now = Instant.now()
    val nextOrder = items.filter(_.collectionId === collectionId).map(_.orderIndex).max.result.map(_.getOrElse(0) + 10)
    val action = for {
      ord   <- nextOrder
      newId <- (items returning items.map(_.id)) += CollectionItem(
                 collectionId = collectionId,
                 itemType     = itemType,
                 itemId       = itemId,
                 orderIndex   = ord,
                 addedAt      = now,
                 curatorNote  = note.map(_.trim).filter(_.nonEmpty)
               )
      _     <- collections.filter(_.id === collectionId).map(_.updatedAt).update(now)
    } yield newId
    db.run(action.transactionally)
  }

  def removeItem(itemRowId: Long): Future[Int] =
    db.run(items.filter(_.id === itemRowId).delete)

  /** Reasigna el `orderIndex` siguiendo el orden recibido (ids paso 10). */
  def reorderItems(collectionId: Long, orderedIds: Seq[Long]): Future[Int] = {
    val now = Instant.now()
    val updates = orderedIds.zipWithIndex.map { case (rid, idx) =>
      items.filter(i => i.id === rid && i.collectionId === collectionId)
        .map(_.orderIndex).update((idx + 1) * 10)
    }
    val action = DBIO.sequence(updates).map(_.sum) andThen
      collections.filter(_.id === collectionId).map(_.updatedAt).update(now)
    db.run(action.transactionally)
  }

  // ──────────────────────── Resolución / búsqueda ────────────────────────

  /**
   * Resuelve los items de una colección a metadatos visibles.
   * Las piezas cuya fuente original fue eliminada vuelven con `existing = false`.
   */
  def resolveItems(collectionId: Long): Future[Seq[ResolvedCollectionItem]] = {
    findItems(collectionId).flatMap { its =>
      val pubIds  = its.filter(_.itemType == CollectionItemType.Publication).map(_.itemId).toSet
      val artIds  = its.filter(_.itemType == CollectionItemType.EditorialArticle).map(_.itemId).toSet

      def csv(s: Set[Long]): String = s.mkString(",")

      val pubsF: Future[Map[Long, (String, String, String)]] =
        if (pubIds.isEmpty) Future.successful(Map.empty)
        else {
          val ids = csv(pubIds)
          db.run(
            sql"""
              SELECT p.id, p.title, p.slug, COALESCE(u.full_name, u.username, '')
                FROM publications p
                LEFT JOIN users u ON u.id = p.user_id
               WHERE p.id IN (#$ids)
            """.as[(Long, String, String, String)]
          ).map(_.map(r => r._1 -> (r._2, r._3, r._4)).toMap)
        }

      val artsF: Future[Map[Long, (String, String, String)]] =
        if (artIds.isEmpty) Future.successful(Map.empty)
        else {
          val ids = csv(artIds)
          db.run(
            sql"""
              SELECT a.id, a.title, a.slug, 'Equipo Editorial'
                FROM editorial_articles a
               WHERE a.id IN (#$ids)
            """.as[(Long, String, String, String)]
          ).map(_.map(r => r._1 -> (r._2, r._3, r._4)).toMap)
        }

      for {
        pubs <- pubsF
        arts <- artsF
      } yield its.map { it =>
        val src = it.itemType match {
          case CollectionItemType.Publication      => pubs.get(it.itemId)
          case CollectionItemType.EditorialArticle => arts.get(it.itemId)
          case _                                   => None
        }
        src match {
          case Some((title, slug, author)) =>
            ResolvedCollectionItem(it, title, slug, author, existing = true)
          case None =>
            ResolvedCollectionItem(it, "(pieza eliminada)", "#", "—", existing = false)
        }
      }
    }
  }

  /**
   * Lista de candidatas para sumar a una colección: publicaciones aprobadas +
   * artículos editoriales publicados. Permite búsqueda libre por título/slug.
   * Excluye las que ya pertenecen a `collectionId`.
   */
  def pickable(collectionId: Long, query: String, limit: Int = 20): Future[Seq[PickableItem]] = {
    val pattern = s"%${query.trim.toLowerCase}%"
    val hasQuery = query.trim.nonEmpty
    val sqlQuery =
      sql"""
        WITH already AS (
          SELECT item_type, item_id FROM collection_items WHERE collection_id = $collectionId
        )
        SELECT 'publication' AS item_type, p.id, p.title, p.slug,
               COALESCE(u.full_name, u.username, '') AS author
          FROM publications p
          LEFT JOIN users u ON u.id = p.user_id
         WHERE p.status = 'approved'
           AND ($hasQuery = FALSE OR LOWER(p.title) LIKE $pattern OR LOWER(p.slug) LIKE $pattern)
           AND NOT EXISTS (SELECT 1 FROM already a WHERE a.item_type = 'publication' AND a.item_id = p.id)
        UNION ALL
        SELECT 'editorial_article' AS item_type, a.id, a.title, a.slug,
               'Equipo Editorial' AS author
          FROM editorial_articles a
         WHERE a.is_published = TRUE
           AND ($hasQuery = FALSE OR LOWER(a.title) LIKE $pattern OR LOWER(a.slug) LIKE $pattern)
           AND NOT EXISTS (SELECT 1 FROM already a2 WHERE a2.item_type = 'editorial_article' AND a2.item_id = a.id)
         ORDER BY 3
         LIMIT $limit
      """.as[(String, Long, String, String, String)]
    db.run(sqlQuery).map(_.map(r => PickableItem(r._1, r._2, r._3, r._4, r._5)))
  }

  // ──────────────────────── Historial ────────────────────────

  def historyOf(collectionId: Long): Future[Seq[CollectionStatusEntry]] =
    db.run(history.filter(_.collectionId === collectionId).sortBy(_.createdAt.desc).result)
}
