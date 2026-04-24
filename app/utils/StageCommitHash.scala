package utils

import java.security.MessageDigest
import models.PublicationStageHistory

/**
 * StageCommitHash — deriva un hash corto estilo git para cada transición
 * de etapa, dándole al autor y al equipo una referencia compacta y única
 * que puede citarse en notificaciones, mensajes y soporte.
 *
 * Determinístico: misma fila de history → mismo hash, siempre.
 */
object StageCommitHash {

  private val md = ThreadLocal.withInitial[MessageDigest](() => MessageDigest.getInstance("SHA-1"))

  /** Hash corto (7 chars) estilo git short SHA. */
  def shortOf(entry: PublicationStageHistory): String = fullOf(entry).take(7)

  /** Hash completo (40 chars) por si se necesita para auditoría. */
  def fullOf(entry: PublicationStageHistory): String = {
    val seed =
      s"${entry.id.getOrElse(0L)}|${entry.publicationId}|${entry.stageId}|" +
      s"${entry.enteredAt.toEpochMilli}|${entry.enteredBy.getOrElse(0L)}|" +
      s"${entry.reason.getOrElse("")}"
    val digest = md.get()
    digest.reset()
    digest.update(seed.getBytes("UTF-8"))
    digest.digest().map("%02x".format(_)).mkString
  }
}
