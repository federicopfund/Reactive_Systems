package services

import scala.concurrent.duration._

/**
 * Lookup runtime de variables de configuración de agentes (Issue #21).
 *
 * Abstracción mínima para que los guardians no dependan del repositorio
 * Slick: durante los tests pasamos un stub, en producción la implementación
 * real es `AgentSettingsService`.
 */
trait AgentSettingsLookup {
  def getInt(key: String): Int
  def getBool(key: String): Boolean
  def getDuration(key: String): FiniteDuration
}

object AgentSettingsLookup {

  /** Lookup que siempre devuelve los defaults declarados en `AgentSettingsCatalog`. */
  val Defaults: AgentSettingsLookup = new AgentSettingsLookup {
    private def raw(key: String): String =
      utils.AgentSettingsCatalog.byKey.get(key).map(_.defaultValue).getOrElse("")
    def getInt(key: String): Int             = scala.util.Try(raw(key).toInt).getOrElse(0)
    def getBool(key: String): Boolean        = raw(key).equalsIgnoreCase("true")
    def getDuration(key: String): FiniteDuration =
      scala.util.Try(raw(key).toLong.seconds).getOrElse(0.seconds)
  }

  /** Lookup en memoria a partir de un Map (defaults de catálogo + overrides). */
  def fromMap(overrides: Map[String, String]): AgentSettingsLookup = new AgentSettingsLookup {
    private def raw(key: String): String =
      overrides.getOrElse(key, utils.AgentSettingsCatalog.byKey.get(key).map(_.defaultValue).getOrElse(""))
    def getInt(key: String): Int             = scala.util.Try(raw(key).toInt).getOrElse(0)
    def getBool(key: String): Boolean        = raw(key).equalsIgnoreCase("true")
    def getDuration(key: String): FiniteDuration =
      scala.util.Try(raw(key).toLong.seconds).getOrElse(0.seconds)
  }
}
