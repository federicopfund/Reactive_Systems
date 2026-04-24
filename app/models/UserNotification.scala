package models

import java.time.Instant

/**
 * Notificación para el usuario.
 *
 * Tipos:
 *  - feedback_sent    : El admin envió un feedback sobre una publicación
 *  - publication_approved : Publicación aprobada
 *  - publication_rejected : Publicación rechazada
 */
object NotificationType extends Enumeration {
  type NotificationType = Value
  val FeedbackSent          = Value("feedback_sent")
  val PublicationApproved   = Value("publication_approved")
  val PublicationRejected   = Value("publication_rejected")
  val PrivateMessage        = Value("private_message")
  val AdminRoleAssigned     = Value("admin_role_assigned")
  val AdminRoleRejected     = Value("admin_role_rejected")
  val AdminRoleRevoked      = Value("admin_role_revoked")

  def icon(nt: String): String = nt match {
    case "feedback_sent"          => "💬"
    case "publication_approved"   => "✅"
    case "publication_rejected"   => "❌"
    case "private_message"        => "✉️"
    case "admin_role_assigned"    => "🔑"
    case "admin_role_rejected"    => "⛔"
    case "admin_role_revoked"     => "🚫"
    case "community_publication"  => "📰"
    case "collection_submitted"   => "📋"
    case "collection_approved"    => "✅"
    case "collection_rejected"    => "🔁"
    case "collection_published"   => "🏛️"
    case _                        => "🔔"
  }
}

case class UserNotification(
  id: Option[Long] = None,
  userId: Long,
  notificationType: String,
  title: String,
  message: String,
  publicationId: Option[Long] = None,
  feedbackId: Option[Long] = None,
  isRead: Boolean = false,
  createdAt: Instant = Instant.now()
)
