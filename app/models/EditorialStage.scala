package models

import java.time.Instant

/**
 * Códigos canónicos de las etapas editoriales.
 * Deben mantenerse sincronizados con el seed de editorial_stages (evolution 14).
 */
object EditorialStageCode {
  val Pitch              = "pitch"
  val Accepted           = "accepted"
  val InDraft            = "in_draft"
  val InCopyEdit         = "in_copy_edit"
  val InTechnicalReview  = "in_technical_review"
  val PendingApproval    = "pending_approval"
  val Scheduled          = "scheduled"
  val Published          = "published"
  val Archived           = "archived"

  val all: Set[String] = Set(
    Pitch, Accepted, InDraft, InCopyEdit, InTechnicalReview,
    PendingApproval, Scheduled, Published, Archived
  )

  /**
   * Mapeo bidireccional con el viejo campo publications.status.
   * Usado durante la transición entre modelos (Sprint 1–3).
   */
  def fromLegacyStatus(status: String): String = status match {
    case "draft"    => InDraft
    case "pending"  => PendingApproval
    case "approved" => Published
    case "rejected" => Archived
    case _          => InDraft
  }

  def toLegacyStatus(code: String): String = code match {
    case InDraft | Accepted | Pitch                                      => "draft"
    case InCopyEdit | InTechnicalReview | PendingApproval | Scheduled    => "pending"
    case Published                                                        => "approved"
    case Archived                                                         => "rejected"
    case _                                                                => "draft"
  }
}

/**
 * Representación de una etapa editorial del catálogo.
 *
 * @param id              PK
 * @param code            Código único usado en toda la app (ver EditorialStageCode)
 * @param label           Etiqueta visible al usuario
 * @param description     Qué ocurre en esta etapa
 * @param orderIndex      Orden canónico en el flujo (10, 20, 30...)
 * @param isTerminal      Si es etapa final (published, archived)
 * @param requiredRole    Rol editorial requerido para recibir piezas en esta etapa
 * @param allowsAuthorEdit Si el autor puede editar el texto estando la pieza en esta etapa
 * @param active          Etapas desactivadas no aparecen en selectores pero preservan historia
 */
case class EditorialStage(
  id: Option[Long] = None,
  code: String,
  label: String,
  description: Option[String] = None,
  orderIndex: Int,
  isTerminal: Boolean = false,
  requiredRole: Option[String] = None,
  allowsAuthorEdit: Boolean = false,
  active: Boolean = true,
  createdAt: Instant = Instant.now()
)
