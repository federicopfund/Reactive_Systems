package repositories

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.EmailVerificationCode
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class EmailVerificationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  
  import dbConfig._
  import profile.api._

  private class EmailVerificationCodesTable(tag: Tag) extends Table[EmailVerificationCode](tag, "email_verification_codes") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def email = column[String]("email")
    def code = column[String]("code")
    def createdAt = column[Instant]("created_at")
    def expiresAt = column[Instant]("expires_at")
    def verified = column[Boolean]("verified")
    def attempts = column[Int]("attempts")

    def * = (id.?, userId, email, code, createdAt, expiresAt, verified, attempts) <> ((EmailVerificationCode.apply _).tupled, EmailVerificationCode.unapply)
  }

  private val codes = TableQuery[EmailVerificationCodesTable]

  def create(code: EmailVerificationCode): Future[EmailVerificationCode] = {
    val insertQuery = codes returning codes.map(_.id) into ((item, id) => item.copy(id = Some(id)))
    db.run(insertQuery += code)
  }

  def findLatestByUserId(userId: Long): Future[Option[EmailVerificationCode]] = {
    val query = codes
      .filter(_.userId === userId)
      .filter(_.verified === false)
      .sortBy(_.createdAt.desc)
      .take(1)
    db.run(query.result.headOption)
  }

  def verify(id: Long): Future[Int] = {
    val query = codes.filter(_.id === id).map(_.verified).update(true)
    db.run(query)
  }

  def incrementAttempts(id: Long): Future[Int] = {
    val query = sql"""
      UPDATE email_verification_codes 
      SET attempts = attempts + 1 
      WHERE id = $id
    """.asUpdate
    db.run(query)
  }

  def deleteExpired(): Future[Int] = {
    val now = Instant.now()
    val query = codes.filter(_.expiresAt < now).filter(_.verified === false).delete
    db.run(query)
  }
}
