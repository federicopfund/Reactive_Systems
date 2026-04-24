package repositories

import javax.inject.{Inject, Singleton}
import models.{PrivateMessage, PrivateMessageWithUsers}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class PrivateMessageRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._
  import slick.jdbc.GetResult

  // Implicit GetResult for raw SQL queries
  private implicit val getPrivateMessageWithUsersResult: GetResult[(Long, Long, Long, Option[Long], String, String, Boolean, Instant, String, String, String, String, Option[String])] =
    GetResult(r => (
      r.nextLong(), r.nextLong(), r.nextLong(), r.nextLongOption(),
      r.nextString(), r.nextString(), r.nextBoolean(), r.nextTimestamp().toInstant,
      r.nextString(), r.nextString(), r.nextString(), r.nextString(),
      r.nextStringOption()
    ))

  private class PrivateMessagesTable(tag: Tag) extends Table[PrivateMessage](tag, "private_messages") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def senderId      = column[Long]("sender_id")
    def receiverId    = column[Long]("receiver_id")
    def publicationId = column[Option[Long]]("publication_id")
    def subject       = column[String]("subject")
    def content       = column[String]("content")
    def isRead        = column[Boolean]("is_read")
    def createdAt     = column[Instant]("created_at")

    def * = (
      id.?, senderId, receiverId, publicationId, subject, content, isRead, createdAt
    ).mapTo[PrivateMessage]
  }

  private val messages = TableQuery[PrivateMessagesTable]

  /** Crear un nuevo mensaje privado */
  def create(msg: PrivateMessage): Future[Long] = {
    val q = messages returning messages.map(_.id)
    db.run(q += msg)
  }

  /** Obtener mensajes recibidos (bandeja de entrada) */
  def findReceived(userId: Long, limit: Int = 50): Future[List[PrivateMessage]] = {
    val q = messages
      .filter(_.receiverId === userId)
      .sortBy(_.createdAt.desc)
      .take(limit)
    db.run(q.result).map(_.toList)
  }

  /** Obtener mensajes enviados */
  def findSent(userId: Long, limit: Int = 50): Future[List[PrivateMessage]] = {
    val q = messages
      .filter(_.senderId === userId)
      .sortBy(_.createdAt.desc)
      .take(limit)
    db.run(q.result).map(_.toList)
  }

  /** Obtener un mensaje por ID (con validación de acceso) */
  def findById(messageId: Long, userId: Long): Future[Option[PrivateMessage]] = {
    val q = messages
      .filter(m => m.id === messageId && (m.senderId === userId || m.receiverId === userId))
    db.run(q.result.headOption)
  }

  /** Marcar un mensaje como leído */
  def markAsRead(messageId: Long, userId: Long): Future[Boolean] = {
    val q = messages
      .filter(m => m.id === messageId && m.receiverId === userId)
      .map(_.isRead)
      .update(true)
    db.run(q).map(_ > 0)
  }

  /** Marcar todos los mensajes recibidos como leídos */
  def markAllAsRead(userId: Long): Future[Int] = {
    val q = messages
      .filter(m => m.receiverId === userId && m.isRead === false)
      .map(_.isRead)
      .update(true)
    db.run(q)
  }

  /** Contar mensajes no leídos */
  def countUnread(userId: Long): Future[Int] = {
    val q = messages
      .filter(m => m.receiverId === userId && m.isRead === false)
      .length
    db.run(q.result)
  }

  /** Obtener mensajes recibidos con datos de usuarios (usando SQL nativo) */
  def findReceivedWithUsers(userId: Long, limit: Int = 50): Future[List[PrivateMessageWithUsers]] = {
    val query = sql"""
      SELECT pm.id, pm.sender_id, pm.receiver_id, pm.publication_id,
             pm.subject, pm.content, pm.is_read, pm.created_at,
             s.username AS sender_username, s.full_name AS sender_fullname,
             r.username AS receiver_username, r.full_name AS receiver_fullname,
             p.title AS publication_title
      FROM private_messages pm
      JOIN users s ON s.id = pm.sender_id
      JOIN users r ON r.id = pm.receiver_id
      LEFT JOIN publications p ON p.id = pm.publication_id
      WHERE pm.receiver_id = $userId
      ORDER BY pm.created_at DESC
      LIMIT $limit
    """.as[(Long, Long, Long, Option[Long], String, String, Boolean, Instant,
            String, String, String, String, Option[String])]

    db.run(query).map { rows =>
      rows.map { case (id, sid, rid, pid, subj, cont, read, cat, su, sf, ru, rf, pt) =>
        PrivateMessageWithUsers(
          message = PrivateMessage(Some(id), sid, rid, pid, subj, cont, read, cat),
          senderUsername = su,
          senderFullName = sf,
          receiverUsername = ru,
          receiverFullName = rf,
          publicationTitle = pt
        )
      }.toList
    }
  }

  /** Obtener mensajes enviados con datos de usuarios */
  def findSentWithUsers(userId: Long, limit: Int = 50): Future[List[PrivateMessageWithUsers]] = {
    val query = sql"""
      SELECT pm.id, pm.sender_id, pm.receiver_id, pm.publication_id,
             pm.subject, pm.content, pm.is_read, pm.created_at,
             s.username AS sender_username, s.full_name AS sender_fullname,
             r.username AS receiver_username, r.full_name AS receiver_fullname,
             p.title AS publication_title
      FROM private_messages pm
      JOIN users s ON s.id = pm.sender_id
      JOIN users r ON r.id = pm.receiver_id
      LEFT JOIN publications p ON p.id = pm.publication_id
      WHERE pm.sender_id = $userId
      ORDER BY pm.created_at DESC
      LIMIT $limit
    """.as[(Long, Long, Long, Option[Long], String, String, Boolean, Instant,
            String, String, String, String, Option[String])]

    db.run(query).map { rows =>
      rows.map { case (id, sid, rid, pid, subj, cont, read, cat, su, sf, ru, rf, pt) =>
        PrivateMessageWithUsers(
          message = PrivateMessage(Some(id), sid, rid, pid, subj, cont, read, cat),
          senderUsername = su,
          senderFullName = sf,
          receiverUsername = ru,
          receiverFullName = rf,
          publicationTitle = pt
        )
      }.toList
    }
  }

  /** Obtener un mensaje con datos de usuarios */
  def findByIdWithUsers(messageId: Long, userId: Long): Future[Option[PrivateMessageWithUsers]] = {
    val query = sql"""
      SELECT pm.id, pm.sender_id, pm.receiver_id, pm.publication_id,
             pm.subject, pm.content, pm.is_read, pm.created_at,
             s.username AS sender_username, s.full_name AS sender_fullname,
             r.username AS receiver_username, r.full_name AS receiver_fullname,
             p.title AS publication_title
      FROM private_messages pm
      JOIN users s ON s.id = pm.sender_id
      JOIN users r ON r.id = pm.receiver_id
      LEFT JOIN publications p ON p.id = pm.publication_id
      WHERE pm.id = $messageId
        AND (pm.sender_id = $userId OR pm.receiver_id = $userId)
      LIMIT 1
    """.as[(Long, Long, Long, Option[Long], String, String, Boolean, Instant,
            String, String, String, String, Option[String])]

    db.run(query).map { rows =>
      rows.headOption.map { case (id, sid, rid, pid, subj, cont, read, cat, su, sf, ru, rf, pt) =>
        PrivateMessageWithUsers(
          message = PrivateMessage(Some(id), sid, rid, pid, subj, cont, read, cat),
          senderUsername = su,
          senderFullName = sf,
          receiverUsername = ru,
          receiverFullName = rf,
          publicationTitle = pt
        )
      }
    }
  }

  // ============================================
  // ADMIN METRICS
  // ============================================

  /** Total de mensajes en el sistema */
  def countAll(): Future[Int] = {
    db.run(messages.length.result)
  }

  /** Total de mensajes no leídos en todo el sistema */
  def countAllUnread(): Future[Int] = {
    db.run(messages.filter(_.isRead === false).length.result)
  }

  /** Total de mensajes leídos */
  def countAllRead(): Future[Int] = {
    db.run(messages.filter(_.isRead === true).length.result)
  }

  /** Mensajes con referencia a publicación */
  def countWithPublication(): Future[Int] = {
    db.run(messages.filter(_.publicationId.isDefined).length.result)
  }

  /** Mensajes directos (sin publicación) */
  def countDirect(): Future[Int] = {
    db.run(messages.filter(_.publicationId.isEmpty).length.result)
  }

  /** Cantidad de usuarios únicos que han enviado mensajes */
  def countUniqueSenders(): Future[Int] = {
    db.run(messages.map(_.senderId).distinct.length.result)
  }

  /** Cantidad de usuarios únicos que han recibido mensajes */
  def countUniqueReceivers(): Future[Int] = {
    db.run(messages.map(_.receiverId).distinct.length.result)
  }

  /** Mensajes enviados en los últimos N días */
  def countInLastDays(days: Int): Future[Int] = {
    val since = Instant.now().minusSeconds(days.toLong * 86400)
    db.run(messages.filter(_.createdAt >= since).length.result)
  }

  /** Tasa de lectura: porcentaje de mensajes leídos */
  def readRate(): Future[Int] = {
    for {
      total <- countAll()
      read  <- countAllRead()
    } yield if (total > 0) ((read.toDouble / total) * 100).round.toInt else 0
  }

  /** Top remitentes (usuarios que más mensajes envían) */
  def topSenders(limit: Int = 5): Future[Seq[(Long, String, Int)]] = {
    val query = sql"""
      SELECT u.id, u.username, COUNT(pm.id)::int AS msg_count
      FROM private_messages pm
      JOIN users u ON u.id = pm.sender_id
      GROUP BY u.id, u.username
      ORDER BY msg_count DESC
      LIMIT $limit
    """.as[(Long, String, Int)]
    db.run(query)
  }

  /** Top receptores (usuarios que más mensajes reciben) */
  def topReceivers(limit: Int = 5): Future[Seq[(Long, String, Int)]] = {
    val query = sql"""
      SELECT u.id, u.username, COUNT(pm.id)::int AS msg_count
      FROM private_messages pm
      JOIN users u ON u.id = pm.receiver_id
      GROUP BY u.id, u.username
      ORDER BY msg_count DESC
      LIMIT $limit
    """.as[(Long, String, Int)]
    db.run(query)
  }

  /** Publicaciones que generan más mensajes */
  def topPublicationsByMessages(limit: Int = 5): Future[Seq[(Long, String, Int)]] = {
    val query = sql"""
      SELECT p.id, p.title, COUNT(pm.id)::int AS msg_count
      FROM private_messages pm
      JOIN publications p ON p.id = pm.publication_id
      WHERE pm.publication_id IS NOT NULL
      GROUP BY p.id, p.title
      ORDER BY msg_count DESC
      LIMIT $limit
    """.as[(Long, String, Int)]
    db.run(query)
  }
}
