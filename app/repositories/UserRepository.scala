package repositories

import models.User
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class UserRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username")
    def email = column[String]("email")
    def passwordHash = column[String]("password_hash")
    def fullName = column[String]("full_name")
    def role = column[String]("role")
    def isActive = column[Boolean]("is_active")
    def createdAt = column[Instant]("created_at")
    def lastLogin = column[Option[Instant]]("last_login")
    def emailVerified = column[Boolean]("email_verified")
    def bio = column[String]("bio")
    def avatarUrl = column[String]("avatar_url")
    def website = column[String]("website")
    def location = column[String]("location")
    def adminApproved = column[Boolean]("admin_approved")
    def adminApprovedBy = column[Option[Long]]("admin_approved_by")
    def adminRequestedAt = column[Option[Instant]]("admin_requested_at")

    def * = (id.?, username, email, passwordHash, fullName, role, isActive, createdAt, lastLogin, emailVerified, bio, avatarUrl, website, location, adminApproved, adminApprovedBy, adminRequestedAt).mapTo[User]
  }

  private val users = TableQuery[UsersTable]

  /**
   * Busca un usuario por username
   */
  def findByUsername(username: String): Future[Option[User]] = {
    db.run(users.filter(u => u.username === username && u.isActive).result.headOption)
  }

  /**
   * Busca un usuario por email
   */
  def findByEmail(email: String): Future[Option[User]] = {
    db.run(users.filter(u => u.email === email && u.isActive).result.headOption)
  }

  /**
   * Busca un usuario por ID
   */
  def findById(id: Long): Future[Option[User]] = {
    db.run(users.filter(_.id === id).result.headOption)
  }

  /**
   * Busca usuarios por lote de emails (case-insensitive).
   * Usado por el broadcaster de newsletter para resolver qué suscriptores
   * son usuarios registrados y por lo tanto pueden recibir una
   * notificación in-app además del email externo.
   */
  def findByEmails(emails: Seq[String]): Future[List[User]] = {
    if (emails.isEmpty) Future.successful(Nil)
    else {
      val normalized = emails.map(_.trim.toLowerCase).distinct
      db.run(users.filter(u => u.email.inSet(normalized) && u.isActive).result).map(_.toList)
    }
  }

  /**
   * Crea un nuevo usuario
   */
  def create(user: User): Future[User] = {
    val insertQuery = users returning users.map(_.id) into ((user, id) => user.copy(id = Some(id)))
    db.run(insertQuery += user)
  }

  /**
   * Actualiza el último login
   */
  def updateLastLogin(id: Long): Future[Int] = {
    val query = users.filter(_.id === id).map(_.lastLogin).update(Some(Instant.now()))
    db.run(query)
  }

  /**
   * Lista todos los usuarios
   */
  def listAll(): Future[Seq[User]] = {
    db.run(users.filter(_.isActive).sortBy(_.createdAt.desc).result)
  }

  /**
   * Cuenta total de usuarios
   */
  def count(): Future[Int] = {
    db.run(users.filter(_.isActive).length.result)
  }

  /**
   * Actualiza un usuario
   */
  def update(id: Long, user: User): Future[Int] = {
    val query = users.filter(_.id === id)
      .map(u => (u.username, u.email, u.fullName))
      .update((user.username, user.email, user.fullName))
    db.run(query)
  }

  /**
   * Desactiva un usuario (soft delete)
   */
  def deactivate(id: Long): Future[Int] = {
    val query = users.filter(_.id === id).map(_.isActive).update(false)
    db.run(query)
  }

  /**
   * Verifica si existe un username
   */
  def usernameExists(username: String): Future[Boolean] = {
    db.run(users.filter(_.username === username).exists.result)
  }

  /**
   * Verifica si existe un email
   */
  def emailExists(email: String): Future[Boolean] = {
    db.run(users.filter(_.email === email).exists.result)
  }

  /**
   * Obtiene usuarios registrados en los últimos N días
   */
  def getUsersRegisteredInLastDays(days: Int): Future[Seq[User]] = {
    val cutoffDate = Instant.now().minusSeconds(days * 24 * 60 * 60)
    db.run(users.filter(u => u.createdAt >= cutoffDate && u.isActive).result)
  }

  /**
   * Cuenta usuarios por rol
   */
  def countByRole(): Future[Map[String, Int]] = {
    val query = users.filter(_.isActive).groupBy(_.role).map { case (role, group) =>
      (role, group.length)
    }
    db.run(query.result).map(_.toMap)
  }

  /**
   * Obtiene usuarios activos en los últimos N días (por lastLogin)
   */
  def getActiveUsersInLastDays(days: Int): Future[Seq[User]] = {
    val cutoffDate = Instant.now().minusSeconds(days * 24 * 60 * 60)
    db.run(users.filter(u => u.lastLogin.isDefined && u.lastLogin >= cutoffDate && u.isActive).result)
  }

  /**
   * Cuenta usuarios que nunca han iniciado sesión
   */
  def countNeverLoggedIn(): Future[Int] = {
    db.run(users.filter(u => u.lastLogin.isEmpty && u.isActive).length.result)
  }

  /**
   * Actualiza el estado de verificación de email
   */
  def updateEmailVerified(id: Long, verified: Boolean): Future[Int] = {
    val query = users.filter(_.id === id).map(_.emailVerified).update(verified)
    db.run(query)
  }

  /**
   * Actualiza el perfil del usuario (bio, avatar, website, location)
   */
  def updateProfile(id: Long, bio: String, avatarUrl: String, website: String, location: String): Future[Int] = {
    val query = users.filter(_.id === id)
      .map(u => (u.bio, u.avatarUrl, u.website, u.location))
      .update((bio, avatarUrl, website, location))
    db.run(query)
  }

  // ── ADMIN MANAGEMENT ──────────────────────────

  /**
   * Verifica si existe algún super_admin en el sistema
   */
  def hasSuperAdmin(): Future[Boolean] =
    db.run(users.filter(u => u.role === "super_admin" && u.isActive).exists.result)

  /**
   * Lista administradores pendientes de aprobación
   */
  def findPendingAdmins(): Future[Seq[User]] =
    db.run(users.filter(u => u.role === "pending_admin" && u.isActive).sortBy(_.adminRequestedAt.desc).result)

  /**
   * Lista todos los administradores aprobados (cualquier rol del backoffice).
   * Se basa en la lista declarada en `utils.RolePolicy.backofficeRoleKeys`
   * para mantener una única fuente de verdad.
   */
  def findApprovedAdmins(): Future[Seq[User]] = {
    val staffRoles = utils.RolePolicy.backofficeRoleKeys
    db.run(users.filter(u => u.role.inSet(staffRoles) && u.isActive).sortBy(_.createdAt.desc).result)
  }

  /**
   * Aprueba un admin pendiente asignándole un rol concreto.
   * Roles permitidos: cualquiera declarado en `utils.RolePolicy.assignableRoles`.
   */
  def approveAdmin(userId: Long, approvedBy: Long, role: String = "editor_jefe"): Future[Int] = {
    val safeRole = utils.RolePolicy.assignableRoles.find(_.key == role)
      .map(_.key)
      .getOrElse("editor_jefe")
    db.run(
      users.filter(u => u.id === userId && u.role === "pending_admin")
        .map(u => (u.role, u.adminApproved, u.adminApprovedBy))
        .update((safeRole, true, Some(approvedBy)))
    )
  }

  /**
   * Cambia el rol de un administrador YA aprobado a otro rol del backoffice.
   * Solo se permite mover entre roles de `assignableRoles`.
   */
  def changeAdminRole(userId: Long, newRole: String): Future[Int] = {
    val safeRole = utils.RolePolicy.assignableRoles.find(_.key == newRole)
      .map(_.key)
      .getOrElse(throw new IllegalArgumentException(s"Rol inválido: $newRole"))
    val staffRoles = utils.RolePolicy.backofficeRoleKeys
    db.run(
      users.filter(u => u.id === userId && u.role.inSet(staffRoles))
        .map(_.role)
        .update(safeRole)
    )
  }

  /**
   * Rechaza un admin pendiente — vuelve a role user
   */
  def rejectAdmin(userId: Long): Future[Int] =
    db.run(
      users.filter(u => u.id === userId && u.role === "pending_admin")
        .map(u => (u.role, u.adminApproved, u.adminApprovedBy, u.adminRequestedAt))
        .update(("user", false, None, None))
    )

  /**
   * Revoca permisos de admin (cualquier rol del backoffice salvo super_admin)
   * y devuelve al usuario al rol "user".
   * Por seguridad, NO permite degradar a un super_admin desde aquí.
   */
  def revokeAdmin(userId: Long): Future[Int] = {
    val revokableRoles = utils.RolePolicy.backofficeRoleKeys - "super_admin"
    db.run(
      users.filter(u => u.id === userId && u.role.inSet(revokableRoles))
        .map(u => (u.role, u.adminApproved, u.adminApprovedBy))
        .update(("user", false, None))
    )
  }

  /**
   * Actualiza el rol de un usuario
   */
  def updateRole(id: Long, role: String): Future[Int] =
    db.run(users.filter(_.id === id).map(_.role).update(role))

  /**
   * Cuenta admins pendientes de aprobación
   */
  def countPendingAdmins(): Future[Int] =
    db.run(users.filter(u => u.role === "pending_admin" && u.isActive).length.result)

  /**
   * Busca un usuario admin (admin o super_admin) por username
   */
  def findAdminByUsername(username: String): Future[Option[User]] =
    db.run(users.filter(u => u.username === username && u.isActive && (u.role === "admin" || u.role === "super_admin")).result.headOption)

  /**
   * Actualiza contraseña de un usuario
   */
  def updatePassword(id: Long, newPasswordHash: String): Future[Int] =
    db.run(users.filter(_.id === id).map(_.passwordHash).update(newPasswordHash))
}
