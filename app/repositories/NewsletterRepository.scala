package repositories

import javax.inject.{Inject, Singleton}
import models.NewsletterSubscriber
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class NewsletterRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class SubscribersTable(tag: Tag) extends Table[NewsletterSubscriber](tag, "newsletter_subscribers") {
    def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email          = column[String]("email")
    def subscribedAt   = column[Instant]("subscribed_at")
    def isActive       = column[Boolean]("is_active")
    def unsubscribedAt = column[Option[Instant]]("unsubscribed_at")
    def ipAddress      = column[Option[String]]("ip_address")

    def * = (id.?, email, subscribedAt, isActive, unsubscribedAt, ipAddress).mapTo[NewsletterSubscriber]
  }

  private val subscribers = TableQuery[SubscribersTable]

  /** Subscribe an email. If already exists and inactive, re-activate. */
  def subscribe(email: String, ip: Option[String] = None): Future[Either[String, NewsletterSubscriber]] = {
    val normalizedEmail = email.trim.toLowerCase
    db.run(subscribers.filter(_.email === normalizedEmail).result.headOption).flatMap {
      case Some(existing) if existing.isActive =>
        Future.successful(Left("Este email ya está suscrito."))
      case Some(existing) =>
        // Re-activate
        val q = subscribers.filter(_.id === existing.id.get)
          .map(s => (s.isActive, s.unsubscribedAt, s.subscribedAt, s.ipAddress))
          .update((true, None, Instant.now(), ip))
        db.run(q).map(_ => Right(existing.copy(isActive = true)))
      case None =>
        val sub = NewsletterSubscriber(
          email = normalizedEmail,
          ipAddress = ip
        )
        db.run((subscribers returning subscribers.map(_.id)) += sub).map { id =>
          Right(sub.copy(id = Some(id)))
        }
    }
  }

  /** Unsubscribe by email */
  def unsubscribe(email: String): Future[Boolean] = {
    val normalizedEmail = email.trim.toLowerCase
    val q = subscribers.filter(s => s.email === normalizedEmail && s.isActive)
      .map(s => (s.isActive, s.unsubscribedAt))
      .update((false, Some(Instant.now())))
    db.run(q).map(_ > 0)
  }

  /** Count active subscribers */
  def countActive(): Future[Int] = {
    db.run(subscribers.filter(_.isActive).length.result)
  }

  /** List active subscribers (for admin) */
  def findAllActive(): Future[List[NewsletterSubscriber]] = {
    db.run(subscribers.filter(_.isActive).sortBy(_.subscribedAt.desc).result).map(_.toList)
  }

  /** List all subscribers including inactive (for admin) */
  def findAll(): Future[List[NewsletterSubscriber]] = {
    db.run(subscribers.sortBy(_.subscribedAt.desc).result).map(_.toList)
  }

  /** Check if email is subscribed */
  def isSubscribed(email: String): Future[Boolean] = {
    val normalizedEmail = email.trim.toLowerCase
    db.run(subscribers.filter(s => s.email === normalizedEmail && s.isActive).exists.result)
  }

  /**
   * Lista de emails activos — usada por el broadcaster cuando una pieza
   * entra a la etapa `published` para difundir a la comunidad suscrita.
   */
  def findActiveEmails(): Future[List[String]] = {
    db.run(subscribers.filter(_.isActive).map(_.email).result).map(_.toList)
  }
}
