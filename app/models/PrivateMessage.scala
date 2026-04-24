package models

import java.time.Instant

/**
 * Mensaje privado entre usuarios.
 *
 * Un usuario registrado puede enviar un mensaje al autor de una publicación.
 * El sistema reactivo (Akka Typed) procesa el envío de forma asíncrona
 * y genera una notificación para el destinatario.
 */
case class PrivateMessage(
  id: Option[Long] = None,
  senderId: Long,
  receiverId: Long,
  publicationId: Option[Long] = None,
  subject: String,
  content: String,
  isRead: Boolean = false,
  createdAt: Instant = Instant.now()
)

case class PrivateMessageWithUsers(
  message: PrivateMessage,
  senderUsername: String,
  senderFullName: String,
  receiverUsername: String,
  receiverFullName: String,
  publicationTitle: Option[String] = None
)
