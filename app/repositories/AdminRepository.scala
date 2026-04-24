package repositories

import models.Admin
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class AdminRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class AdminsTable(tag: Tag) extends Table[Admin](tag, "admins") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username")
    def email = column[String]("email")
    def passwordHash = column[String]("password_hash")
    def role = column[String]("role")
    def createdAt = column[Instant]("created_at")
    def lastLogin = column[Option[Instant]]("last_login")

    def * = (id.?, username, email, passwordHash, role, createdAt, lastLogin).mapTo[Admin]
  }

  private val admins = TableQuery[AdminsTable]

  /**
   * Busca un admin por username
   */
  def findByUsername(username: String): Future[Option[Admin]] = {
    db.run(admins.filter(_.username === username).result.headOption)
  }

  /**
   * Busca un admin por email
   */
  def findByEmail(email: String): Future[Option[Admin]] = {
    db.run(admins.filter(_.email === email).result.headOption)
  }

  /**
   * Crea un nuevo admin
   */
  def create(admin: Admin): Future[Admin] = {
    val insertQuery = admins returning admins.map(_.id) into ((admin, id) => admin.copy(id = Some(id)))
    db.run(insertQuery += admin)
  }

  /**
   * Actualiza el último login
   */
  def updateLastLogin(id: Long): Future[Int] = {
    val query = admins.filter(_.id === id).map(_.lastLogin).update(Some(Instant.now()))
    db.run(query)
  }

  /**
   * Actualiza la contraseña de un admin
   */
  def updatePassword(id: Long, newPasswordHash: String): Future[Int] = {
    val query = admins.filter(_.id === id).map(_.passwordHash).update(newPasswordHash)
    db.run(query)
  }

  /**
   * Verifica si existe al menos un admin
   */
  def hasAdmins(): Future[Boolean] = {
    db.run(admins.length.result).map(_ > 0)
  }

  /**
   * Lista todos los admins
   */
  def listAll(): Future[Seq[Admin]] = {
    db.run(admins.sortBy(_.createdAt.desc).result)
  }

  /**
   * Cuenta total de admins
   */
  def count(): Future[Int] = {
    db.run(admins.length.result)
  }
}
