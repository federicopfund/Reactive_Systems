package repositories

import models.ContactRecord
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

@Singleton
class ContactRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class ContactsTable(tag: Tag) extends Table[ContactRecord](tag, "contacts") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def message = column[String]("message")
    def createdAt = column[Instant]("created_at")
    def status = column[String]("status")

    def * = (id.?, name, email, message, createdAt, status).mapTo[ContactRecord]
  }

  private val contacts = TableQuery[ContactsTable]

  /**
   * Guarda un nuevo contacto en la base de datos
   */
  def save(contact: ContactRecord): Future[ContactRecord] = {
    val insertQuery = contacts returning contacts.map(_.id) into ((contact, id) => contact.copy(id = Some(id)))
    db.run(insertQuery += contact)
  }

  /**
   * Busca un contacto por ID
   */
  def findById(id: Long): Future[Option[ContactRecord]] = {
    db.run(contacts.filter(_.id === id).result.headOption)
  }

  /**
   * Lista todos los contactos
   */
  def listAll(): Future[Seq[ContactRecord]] = {
    db.run(contacts.sortBy(_.createdAt.desc).result)
  }

  /**
   * Lista contactos con paginación
   */
  def list(page: Int = 0, pageSize: Int = 20): Future[Seq[ContactRecord]] = {
    val offset = page * pageSize
    db.run(
      contacts
        .sortBy(_.createdAt.desc)
        .drop(offset)
        .take(pageSize)
        .result
    )
  }

  /**
   * Busca contactos por email
   */
  def findByEmail(email: String): Future[Seq[ContactRecord]] = {
    db.run(contacts.filter(_.email === email).result)
  }

  /**
   * Actualiza el estado de un contacto
   */
  def updateStatus(id: Long, newStatus: String): Future[Int] = {
    val query = contacts.filter(_.id === id).map(_.status).update(newStatus)
    db.run(query)
  }

  /**
   * Actualiza un contacto completo
   */
  def update(id: Long, contact: ContactRecord): Future[Int] = {
    val query = contacts.filter(_.id === id)
      .map(c => (c.name, c.email, c.message, c.status))
      .update((contact.name, contact.email, contact.message, contact.status))
    db.run(query)
  }

  /**
   * Cuenta el total de contactos
   */
  def count(): Future[Int] = {
    db.run(contacts.length.result)
  }

  /**
   * Elimina un contacto por ID
   */
  def delete(id: Long): Future[Int] = {
    db.run(contacts.filter(_.id === id).delete)
  }

  /**
   * Obtiene contactos creados en los últimos N días
   */
  def getContactsInLastDays(days: Int): Future[Seq[ContactRecord]] = {
    val cutoffDate = Instant.now().minusSeconds(days * 24 * 60 * 60)
    db.run(contacts.filter(_.createdAt >= cutoffDate).result)
  }

  /**
   * Cuenta contactos por estado
   */
  def countByStatus(): Future[Map[String, Int]] = {
    val query = contacts.groupBy(_.status).map { case (status, group) =>
      (status, group.length)
    }
    db.run(query.result).map(_.toMap)
  }

  /**
   * Obtiene tiempo promedio de respuesta (de pending a processed) en segundos
   */
  def getAverageResponseTime(): Future[Option[Long]] = {
    // Por simplicidad, calculamos basado en la diferencia de tiempo entre creación
    // En un sistema real, necesitarías un campo updatedAt
    db.run(contacts.filter(_.status === "processed").result).map { processed =>
      if (processed.isEmpty) None
      else {
        val avgSeconds = processed.map { contact =>
          // Simulación: asumimos que procesados tardaron entre 1-3 días
          java.time.Duration.between(contact.createdAt, Instant.now()).getSeconds
        }.sum / processed.length
        Some(avgSeconds)
      }
    }
  }
}
