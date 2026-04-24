package models

import java.time.Instant

/** Tipo de valor persistido en `agent_settings`. */
sealed trait AgentSettingType { def key: String }
object AgentSettingType {
  case object IntT      extends AgentSettingType { val key = "int" }
  case object LongT     extends AgentSettingType { val key = "long" }
  case object BoolT     extends AgentSettingType { val key = "bool" }
  case object StringT   extends AgentSettingType { val key = "string" }
  /** Duraciones se guardan como entero de segundos. */
  case object DurationT extends AgentSettingType { val key = "duration" }

  def fromKey(s: String): AgentSettingType = s match {
    case "int"      => IntT
    case "long"     => LongT
    case "bool"     => BoolT
    case "duration" => DurationT
    case _          => StringT
  }
}

/** Categoría visual para agrupar el formulario admin. */
sealed trait AgentSettingCategory { def key: String; def label: String }
object AgentSettingCategory {
  case object Supervision extends AgentSettingCategory { val key = "supervision"; val label = "Supervisión (restarts)" }
  case object Backoff     extends AgentSettingCategory { val key = "backoff";     val label = "Backoff exponencial" }
  case object Heartbeat   extends AgentSettingCategory { val key = "heartbeat";   val label = "Heartbeat / ping" }
  case object CB          extends AgentSettingCategory { val key = "cb";          val label = "Circuit Breakers" }
  case object Pipeline    extends AgentSettingCategory { val key = "pipeline";    val label = "Pipeline / Saga" }
  case object Engines     extends AgentSettingCategory { val key = "engines";     val label = "Kill-switch de engines" }
  case object Dashboard   extends AgentSettingCategory { val key = "dashboard";   val label = "Dashboard de observabilidad" }

  val all: Seq[AgentSettingCategory] = Seq(Supervision, Backoff, Heartbeat, CB, Pipeline, Engines, Dashboard)
  def fromKey(s: String): AgentSettingCategory = all.find(_.key == s).getOrElse(Supervision)
}

/**
 * Definición estática de un setting (catálogo). Las definiciones viven en
 * `AgentSettingsCatalog`; la tabla `agent_settings` solo guarda overrides.
 */
final case class AgentSettingDef(
  key:             String,
  label:           String,
  description:     String,
  category:        AgentSettingCategory,
  valueType:       AgentSettingType,
  defaultValue:    String,
  requiresRestart: Boolean,
  /** Texto de ayuda con unidad (ej. "segundos", "intentos"). */
  unit:            Option[String] = None,
  min:             Option[Long]   = None,
  max:             Option[Long]   = None
)

/** Valor concreto persistido. */
final case class AgentSetting(
  key:       String,
  valueText: String,
  valueType: AgentSettingType,
  category:  AgentSettingCategory,
  updatedAt: Instant,
  updatedBy: Option[Long]
)

/** Entrada de auditoría. */
final case class AgentSettingAudit(
  id:            Option[Long],
  settingKey:    String,
  oldValue:      Option[String],
  newValue:      String,
  changedAt:     Instant,
  changedBy:     Option[Long],
  changedByName: Option[String],
  reason:        String   // "set" | "reset"
)
