package utils

import models.{AgentSettingCategory, AgentSettingDef, AgentSettingType}

/**
 * Catálogo único de variables configurables de los agentes.
 * Es la fuente de verdad: el formulario admin se renderiza a partir de
 * `AgentSettingsCatalog.all` y los defaults se aplican cuando la tabla
 * `agent_settings` no tiene override.
 *
 * Convenciones de naming: `<capa>.<concepto>[.subkey]`
 *   - supervision.{domain|crosscut|infra}.{maxRestarts|resetBackoffSec}
 *   - backoff.{domain|crosscut|infra}.{minSec|maxSec}
 *   - heartbeat.{domain|crosscut|infra}.intervalSec
 *   - cb.moderation.{failureThreshold|resetSec}
 *   - cb.notification.{maxFailures|callTimeoutSec|resetSec}
 *   - pipeline.{stepTimeoutSec|compensationTimeoutSec}
 *   - engines.{contact|message|publication|gamification|notification|moderation|analytics|eventbus|pipeline}.enabled
 *   - dashboard.pollingSec
 */
object AgentSettingsCatalog {
  import AgentSettingCategory._
  import AgentSettingType._

  private def i(key: String, label: String, desc: String, cat: AgentSettingCategory, default: Int,
                requiresRestart: Boolean, unit: Option[String] = None, min: Long = 1, max: Long = Int.MaxValue.toLong) =
    AgentSettingDef(key, label, desc, cat, IntT, default.toString, requiresRestart, unit, Some(min), Some(max))

  private def d(key: String, label: String, desc: String, cat: AgentSettingCategory, defaultSec: Int,
                requiresRestart: Boolean, min: Long = 1, max: Long = 24L * 3600) =
    AgentSettingDef(key, label, desc, cat, DurationT, defaultSec.toString, requiresRestart, Some("segundos"), Some(min), Some(max))

  private def b(key: String, label: String, desc: String, cat: AgentSettingCategory, default: Boolean,
                requiresRestart: Boolean) =
    AgentSettingDef(key, label, desc, cat, BoolT, default.toString, requiresRestart)

  // ── Supervisión por capa ──
  private val supervision: Seq[AgentSettingDef] = Seq(
    i("supervision.domain.maxRestarts",     "Domain · Máx. reinicios",   "Tope de reinicios de cada hijo del DomainGuardian antes de marcar Dead.",   Supervision, 5,  requiresRestart = true, Some("intentos"), 1, 100),
    d("supervision.domain.resetBackoffSec", "Domain · Reset backoff",    "Tras este tiempo sin fallos, el contador de reinicios vuelve a cero.",     Supervision, 60,  requiresRestart = true),
    i("supervision.crosscut.maxRestarts",   "CrossCut · Máx. reinicios", "Tope de reinicios para Notification, Moderation y Analytics.",             Supervision, 5,  requiresRestart = true, Some("intentos"), 1, 100),
    d("supervision.crosscut.resetBackoffSec","CrossCut · Reset backoff", "Tras este tiempo sin fallos, el contador de reinicios vuelve a cero.",     Supervision, 60,  requiresRestart = true),
    i("supervision.infra.maxRestarts",      "Infra · Máx. reinicios",    "Tope de reinicios de EventBus y Pipeline.",                                Supervision, 5,  requiresRestart = true, Some("intentos"), 1, 100),
    d("supervision.infra.resetBackoffSec",  "Infra · Reset backoff",     "Tras este tiempo sin fallos, el contador de reinicios vuelve a cero.",     Supervision, 60,  requiresRestart = true)
  )

  // ── Backoff por capa ──
  private val backoff: Seq[AgentSettingDef] = Seq(
    d("backoff.domain.minSec",   "Domain · Backoff mínimo",   "Espera mínima antes del primer reintento.",        Backoff, 1,  requiresRestart = true, 1, 600),
    d("backoff.domain.maxSec",   "Domain · Backoff máximo",   "Tope superior del backoff exponencial.",           Backoff, 30, requiresRestart = true, 1, 3600),
    d("backoff.crosscut.minSec", "CrossCut · Backoff mínimo", "Espera mínima antes del primer reintento.",        Backoff, 1,  requiresRestart = true, 1, 600),
    d("backoff.crosscut.maxSec", "CrossCut · Backoff máximo", "Tope superior del backoff exponencial.",           Backoff, 30, requiresRestart = true, 1, 3600),
    d("backoff.infra.minSec",    "Infra · Backoff mínimo",    "Espera mínima antes del primer reintento.",        Backoff, 1,  requiresRestart = true, 1, 600),
    d("backoff.infra.maxSec",    "Infra · Backoff máximo",    "Tope superior del backoff exponencial.",           Backoff, 30, requiresRestart = true, 1, 3600)
  )

  // ── Heartbeat ──
  private val heartbeat: Seq[AgentSettingDef] = Seq(
    d("heartbeat.domain.intervalSec",   "Domain · Intervalo de ping",   "Cada cuántos segundos el guardian refresca el snapshot de salud.", Heartbeat, 30, requiresRestart = true, 5, 600),
    d("heartbeat.crosscut.intervalSec", "CrossCut · Intervalo de ping", "Cada cuántos segundos el guardian refresca el snapshot de salud.", Heartbeat, 30, requiresRestart = true, 5, 600),
    d("heartbeat.infra.intervalSec",    "Infra · Intervalo de ping",    "Cada cuántos segundos el guardian refresca el snapshot de salud.", Heartbeat, 30, requiresRestart = true, 5, 600)
  )

  // ── Circuit Breakers ──
  private val cb: Seq[AgentSettingDef] = Seq(
    i("cb.moderation.failureThreshold", "CB Moderation · Umbral de fallos",    "Cuántos fallos consecutivos abren el CB y disparan respuestas 'pending_review'.", CB, 3,  requiresRestart = true, Some("fallos"), 1, 50),
    d("cb.moderation.resetSec",         "CB Moderation · Reset",               "Segundos hasta que el CB pase a half-open.",                                     CB, 30, requiresRestart = true, 1, 600),
    i("cb.notification.maxFailures",    "CB Notification (email) · Máx fallos","Cuántos envíos fallidos abren el CB de email.",                                  CB, 3,  requiresRestart = true, Some("fallos"), 1, 50),
    d("cb.notification.callTimeoutSec", "CB Notification · Call timeout",      "Tiempo máximo por llamada SMTP antes de contar como fallo.",                     CB, 10, requiresRestart = true, 1, 120),
    d("cb.notification.resetSec",       "CB Notification · Reset",             "Segundos hasta que el CB pase a half-open.",                                     CB, 60, requiresRestart = true, 1, 600)
  )

  // ── Pipeline / Saga ──
  private val pipeline: Seq[AgentSettingDef] = Seq(
    d("pipeline.stepTimeoutSec",         "Saga · Timeout por paso",      "Tiempo máximo de cada paso de la saga antes de disparar compensación.", Pipeline, 30, requiresRestart = true, 1, 600),
    d("pipeline.compensationTimeoutSec", "Saga · Timeout de compensación","Tiempo máximo total para la fase de compensación.",                     Pipeline, 60, requiresRestart = true, 1, 600)
  )

  // ── Kill-switches por engine (RUNTIME — efecto inmediato) ──
  private val engines: Seq[AgentSettingDef] = Seq(
    "contact", "message", "publication", "gamification",
    "notification", "moderation", "analytics",
    "eventbus", "pipeline"
  ).map { name =>
    b(s"engines.$name.enabled",
      s"Engine $name · Habilitado",
      s"Si se desactiva, el guardian descarta los mensajes dirigidos al engine '$name' (kill-switch suave).",
      Engines, default = true, requiresRestart = false)
  }

  // ── Dashboard ──
  private val dashboard: Seq[AgentSettingDef] = Seq(
    i("dashboard.pollingSec", "Dashboard · Intervalo de polling",
      "Cada cuántos segundos el dashboard de agentes pide el snapshot.",
      Dashboard, 5, requiresRestart = false, Some("segundos"), 2, 120)
  )

  val all: Seq[AgentSettingDef] =
    supervision ++ backoff ++ heartbeat ++ cb ++ pipeline ++ engines ++ dashboard

  val byKey: Map[String, AgentSettingDef] = all.map(d => d.key -> d).toMap

  def find(key: String): Option[AgentSettingDef] = byKey.get(key)

  /** Categorías en orden de presentación, cada una con sus settings. */
  val grouped: Seq[(AgentSettingCategory, Seq[AgentSettingDef])] =
    AgentSettingCategory.all.map(cat => cat -> all.filter(_.category == cat))

  // ── Validación / parsing ──
  def parseAndValidate(d: AgentSettingDef, raw: String): Either[String, String] = {
    val v = raw.trim
    d.valueType match {
      case AgentSettingType.BoolT =>
        v.toLowerCase match {
          case "true" | "false" => Right(v.toLowerCase)
          case _ => Left("Valor booleano debe ser 'true' o 'false'.")
        }
      case AgentSettingType.IntT | AgentSettingType.LongT | AgentSettingType.DurationT =>
        scala.util.Try(v.toLong).toEither.left.map(_ => "Valor numérico inválido.").flatMap { n =>
          (d.min, d.max) match {
            case (Some(mn), _) if n < mn => Left(s"Valor mínimo permitido: $mn.")
            case (_, Some(mx)) if n > mx => Left(s"Valor máximo permitido: $mx.")
            case _ => Right(n.toString)
          }
        }
      case AgentSettingType.StringT =>
        if (v.isEmpty) Left("No puede estar vacío.") else Right(v)
    }
  }
}
