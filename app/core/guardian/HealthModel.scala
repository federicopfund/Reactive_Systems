package core.guardian

import java.time.Instant

/**
 * Modelo de salud compartido entre los 3 guardians (Issue #15).
 *
 * Un `ChildHealth` describe el estado operacional de un actor hijo de un
 * guardian: estado lógico (`Healthy | Degraded | Dead`), número de reinicios,
 * último error capturado, latencia del último ping al mailbox y timestamp.
 *
 * Un `GuardianHealth` agrega los `ChildHealth` de todos los hijos de un
 * guardian, con un flag `healthy` que es `true` solo cuando todos los hijos
 * están `Healthy`.
 */

sealed trait ChildStatus { def name: String }
object ChildStatus {
  case object Healthy                                          extends ChildStatus { val name = "healthy"  }
  final case class Degraded(restarts: Int, lastError: String)  extends ChildStatus { val name = "degraded" }
  case object Dead                                             extends ChildStatus { val name = "dead"     }
}

final case class ChildHealth(
  child:        String,
  status:       ChildStatus,
  restarts:     Int,
  lastError:    Option[String],
  lastPingMs:   Long,        // -1 si no hay medición todavía
  updatedAt:    Instant
)

final case class GuardianHealth(
  layer:    String,
  healthy:  Boolean,
  children: Map[String, ChildHealth]
)
