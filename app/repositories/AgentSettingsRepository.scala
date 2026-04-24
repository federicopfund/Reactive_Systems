package repositories

import javax.inject.{Inject, Singleton}
import models.{AgentSetting, AgentSettingAudit, AgentSettingCategory, AgentSettingType}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Persistencia de overrides de configuración de agentes (Issue #21).
 * Los defaults están en `utils.AgentSettingsCatalog`; la tabla solo guarda
 * valores que el admin haya cambiado.
 */
@Singleton
class AgentSettingsRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private class Settings(tag: Tag) extends Table[AgentSetting](tag, "agent_settings") {
    def key       = column[String]("setting_key", O.PrimaryKey)
    def value     = column[String]("value_text")
    def vtype     = column[String]("value_type")
    def category  = column[String]("category")
    def updatedAt = column[Instant]("updated_at")
    def updatedBy = column[Option[Long]]("updated_by")

    def * = (key, value, vtype, category, updatedAt, updatedBy).<>(
      { case (k, v, t, c, ts, by) =>
        AgentSetting(k, v, AgentSettingType.fromKey(t), AgentSettingCategory.fromKey(c), ts, by)
      },
      (s: AgentSetting) => Some((s.key, s.valueText, s.valueType.key, s.category.key, s.updatedAt, s.updatedBy))
    )
  }
  private val settings = TableQuery[Settings]

  private class AuditT(tag: Tag) extends Table[AgentSettingAudit](tag, "agent_settings_audit") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def key         = column[String]("setting_key")
    def oldValue    = column[Option[String]]("old_value")
    def newValue    = column[String]("new_value")
    def changedAt   = column[Instant]("changed_at")
    def changedBy   = column[Option[Long]]("changed_by")
    def changedName = column[Option[String]]("changed_by_name")
    def reason      = column[String]("reason")

    def * = (id.?, key, oldValue, newValue, changedAt, changedBy, changedName, reason).<>(
      AgentSettingAudit.tupled, AgentSettingAudit.unapply
    )
  }
  private val audit = TableQuery[AuditT]

  def all(): Future[Seq[AgentSetting]] = db.run(settings.result)

  def upsert(s: AgentSetting, oldValue: Option[String], who: Option[Long], whoName: Option[String], reason: String): Future[Int] = {
    val auditRow = AgentSettingAudit(None, s.key, oldValue, s.valueText, s.updatedAt, who, whoName, reason)
    val action = (for {
      _ <- settings.insertOrUpdate(s)
      _ <- audit += auditRow
    } yield 1).transactionally
    db.run(action)
  }

  def deleteOne(key: String, who: Option[Long], whoName: Option[String], oldValue: Option[String]): Future[Int] = {
    val auditRow = AgentSettingAudit(None, key, oldValue, "<default>", Instant.now(), who, whoName, "reset")
    val action = (for {
      n <- settings.filter(_.key === key).delete
      _ <- audit += auditRow
    } yield n).transactionally
    db.run(action)
  }

  def lastAudit(limit: Int = 50): Future[Seq[AgentSettingAudit]] =
    db.run(audit.sortBy(_.changedAt.desc).take(limit).result)
}
