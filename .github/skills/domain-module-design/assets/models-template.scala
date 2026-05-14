package domains.<dominio>.models

import java.time.Instant
import play.api.libs.json.{Json, OFormat}

// ── Entidad principal ──────────────────────────────────────────────────────
// Representa la fila completa de DB. Solo se usa en el dominio y adapters.

case class <Nombre>(
  id:        Long,
  // agrega los campos del dominio aquí
  createdAt: Instant,
  updatedAt: Instant
)

object <Nombre> {
  implicit val format: OFormat[<Nombre>] = Json.format[<Nombre>]
}

// ── DTO de creación ────────────────────────────────────────────────────────
// Recibido desde el controller. Sin id ni timestamps (los genera la DB).

case class Create<Nombre>Request(
  // agrega los campos requeridos aquí
)

object Create<Nombre>Request {
  implicit val format: OFormat[Create<Nombre>Request] = Json.format[Create<Nombre>Request]
}

// ── DTO de actualización ───────────────────────────────────────────────────
// Campos opcionales: solo los enviados se actualizan (PATCH semántico).

case class Update<Nombre>Request(
  // agrega los campos actualizables como Option[T]
)

object Update<Nombre>Request {
  implicit val format: OFormat[Update<Nombre>Request] = Json.format[Update<Nombre>Request]
}

// ── Vista pública ──────────────────────────────────────────────────────────
// Enviada al cliente. Puede omitir campos internos (ej: hash de contraseña).

case class <Nombre>View(
  id: Long,
  // agrega solo los campos que deben ser públicos
)

object <Nombre>View {
  implicit val format: OFormat[<Nombre>View] = Json.format[<Nombre>View]

  def from(entity: <Nombre>): <Nombre>View = <Nombre>View(
    id = entity.id,
    // mapear campos
  )
}
