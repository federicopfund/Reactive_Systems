package services

import javax.inject.{Inject, Singleton}
import models.AgentSetting
import repositories.AgentSettingsRepository
import utils.AgentSettingsCatalog

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Snapshot in-memory de los settings de agentes.
 * - Lee la tabla al boot y la mantiene en un `AtomicReference[Map]`.
 * - Cualquier código que necesite un valor llama `getInt/getBool/getDuration/...`
 *   y obtiene el override (si existe) o el default del catálogo.
 * - El controller admin, al guardar, llama `refresh()` para recargar el snapshot.
 */
@Singleton
class AgentSettingsService @Inject()(
  repo: AgentSettingsRepository
)(implicit ec: ExecutionContext) extends AgentSettingsLookup {

  private val snapshot = new AtomicReference[Map[String, String]](Map.empty)

  // Carga inicial sincrónica (acotada). Si la tabla no existe aún, queda vacío.
  try {
    val rows = Await.result(repo.all(), 5.seconds)
    snapshot.set(rows.map(s => s.key -> s.valueText).toMap)
  } catch { case _: Throwable => snapshot.set(Map.empty) }

  /** Recarga el snapshot desde DB. Llamar tras cada escritura. */
  def refresh(): Future[Unit] = repo.all().map { rows =>
    snapshot.set(rows.map(s => s.key -> s.valueText).toMap)
  }

  // ── Lectura tipada ──
  private def raw(key: String): String =
    snapshot.get().getOrElse(key, AgentSettingsCatalog.byKey.get(key).map(_.defaultValue).getOrElse(""))

  def getString(key: String): String       = raw(key)
  def getInt(key: String): Int             = scala.util.Try(raw(key).toInt).getOrElse(0)
  def getLong(key: String): Long           = scala.util.Try(raw(key).toLong).getOrElse(0L)
  def getBool(key: String): Boolean        = raw(key).equalsIgnoreCase("true")
  def getDuration(key: String): FiniteDuration = scala.util.Try(raw(key).toLong.seconds).getOrElse(0.seconds)

  /** Snapshot crudo (key -> value) — usado por el endpoint runtime y el form admin. */
  def runtimeSnapshot: Map[String, String] = snapshot.get()
}
