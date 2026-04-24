package repositories

import javax.inject.{Inject, Singleton}
import models.UserNotification
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class UserNotificationRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class NotificationsTable(tag: Tag) extends Table[UserNotification](tag, "user_notifications") {
    def id               = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId           = column[Long]("user_id")
    def notificationType = column[String]("notification_type")
    def title            = column[String]("title")
    def message          = column[String]("message")
    def publicationId    = column[Option[Long]]("publication_id")
    def feedbackId       = column[Option[Long]]("feedback_id")
    def isRead           = column[Boolean]("is_read")
    def createdAt        = column[Instant]("created_at")

    def * = (
      id.?, userId, notificationType, title, message,
      publicationId, feedbackId, isRead, createdAt
    ).mapTo[UserNotification]
  }

  private val notifications = TableQuery[NotificationsTable]

  /** Crear notificación */
  def create(notification: UserNotification): Future[Long] = {
    val q = notifications returning notifications.map(_.id)
    db.run(q += notification)
  }

  /**
   * Emitir una misma notificación a un lote de usuarios en un solo INSERT.
   * Usado por el broadcaster de newsletter cuando una pieza entra a
   * `published` y todos los suscriptores registrados reciben aviso.
   */
  def createBroadcast(
    userIds: Seq[Long],
    notificationType: String,
    title: String,
    message: String,
    publicationId: Option[Long] = None
  ): Future[Int] = {
    if (userIds.isEmpty) Future.successful(0)
    else {
      val now = Instant.now()
      val rows = userIds.map { uid =>
        UserNotification(
          userId           = uid,
          notificationType = notificationType,
          title            = title,
          message          = message,
          publicationId    = publicationId,
          createdAt        = now
        )
      }
      db.run(notifications ++= rows).map(_.getOrElse(0))
    }
  }

  /** Obtener notificaciones de un usuario (más recientes primero) */
  def findByUserId(userId: Long, limit: Int = 30): Future[List[UserNotification]] = {
    val q = notifications
      .filter(_.userId === userId)
      .sortBy(_.createdAt.desc)
      .take(limit)
    db.run(q.result).map(_.toList)
  }

  /**
   * Notificaciones asociadas a una publicación de un usuario,
   * ordenadas cronológicamente (más viejas primero) — base del hilo
   * de trazabilidad mostrado al autor.
   */
  def findByPublicationForUser(userId: Long, publicationId: Long): Future[List[UserNotification]] = {
    val q = notifications
      .filter(n => n.userId === userId && n.publicationId === publicationId)
      .sortBy(_.createdAt.asc)
    db.run(q.result).map(_.toList)
  }

  /** Contar no leídas */
  def countUnread(userId: Long): Future[Int] = {
    val q = notifications
      .filter(n => n.userId === userId && n.isRead === false)
      .length
    db.run(q.result)
  }

  /** Marcar una como leída */
  def markAsRead(notificationId: Long, userId: Long): Future[Boolean] = {
    val q = notifications
      .filter(n => n.id === notificationId && n.userId === userId)
      .map(_.isRead)
      .update(true)
    db.run(q).map(_ > 0)
  }

  /** Marcar todas como leídas */
  def markAllAsRead(userId: Long): Future[Int] = {
    val q = notifications
      .filter(n => n.userId === userId && n.isRead === false)
      .map(_.isRead)
      .update(true)
    db.run(q)
  }
}
