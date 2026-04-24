package repositories

import javax.inject.{Inject, Singleton}
import models.ManifestoPillar
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

/**
 * Pilares del Manifiesto Reactivo. Tabla muy pequeña (4 filas),
 * cambia raramente, perfecta para servir desde DB.
 */
@Singleton
class ManifestoPillarRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Pillars(tag: Tag)
      extends Table[ManifestoPillar](tag, "manifesto_pillars") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def pillarNumber = column[Int]("pillar_number")
    def romanNumeral = column[String]("roman_numeral")
    def name         = column[String]("name")
    def description  = column[String]("description")
    def tagsPipe     = column[String]("tags_pipe")
    def accentColor  = column[Option[String]]("accent_color")
    def orderIndex   = column[Int]("order_index")
    def active       = column[Boolean]("active")
    def createdAt    = column[Instant]("created_at")
    def updatedAt    = column[Instant]("updated_at")

    def * = (
      id.?, pillarNumber, romanNumeral, name, description, tagsPipe,
      accentColor, orderIndex, active, createdAt, updatedAt
    ).mapTo[ManifestoPillar]
  }

  private val pillars = TableQuery[Pillars]

  /** Todos los pilares activos en orden canónico. */
  def findActive(): Future[Seq[ManifestoPillar]] =
    db.run(pillars.filter(_.active).sortBy(_.orderIndex.asc).result)

  def findAll(): Future[Seq[ManifestoPillar]] =
    db.run(pillars.sortBy(_.orderIndex.asc).result)
}
