package domains.<dominio>.repositories

import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import domains.<dominio>.models._
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class <Nombre>Repository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  // ── Tabla Slick ───────────────────────────────────────────────────────────

  private class <Nombre>Table(tag: Tag) extends Table[<Nombre>](tag, "<nombre>s") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    // agrega columnas del dominio aquí

    def * = (id, createdAt, updatedAt).mapTo[<Nombre>]
  }

  private val table = TableQuery[<Nombre>Table]

  // ── Operaciones ───────────────────────────────────────────────────────────

  /** Retorna todos los registros. Paginar antes de usar en producción. */
  def findAll(): Future[Seq[<Nombre>]] =
    db.run(table.result)

  /** Busca por id. Retorna None si no existe. */
  def findById(id: Long): Future[Option[<Nombre>]] =
    db.run(table.filter(_.id === id).result.headOption)

  /** Inserta y retorna la entidad con id asignado. */
  def create(req: Create<Nombre>Request): Future[<Nombre>] = {
    val now = Instant.now()
    val entity = <Nombre>(
      id        = 0L, // autoincrement
      createdAt = now,
      updatedAt = now
      // mapear campos de req aquí
    )
    val insertQuery = (table returning table.map(_.id)
      into ((row, id) => row.copy(id = id))) += entity

    db.run(insertQuery)
  }

  /** Actualiza campos editables. Retorna la entidad actualizada o None. */
  def update(id: Long, req: Update<Nombre>Request): Future[Option[<Nombre>]] =
    db.run(table.filter(_.id === id).result.headOption).flatMap {
      case None => Future.successful(None)
      case Some(existing) =>
        val updated = existing.copy(
          updatedAt = Instant.now()
          // aplicar campos de req aquí
        )
        db.run(table.filter(_.id === id).update(updated)).map(_ => Some(updated))
    }

  /** Elimina por id. Retorna true si existía. */
  def delete(id: Long): Future[Boolean] =
    db.run(table.filter(_.id === id).delete).map(_ > 0)
}
