package models

import java.time.Instant

case class PublicationComment(
  id: Option[Long] = None,
  publicationId: Long,
  userId: Long,
  content: String,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

case class CommentWithAuthor(
  comment: PublicationComment,
  authorUsername: String,
  authorFullName: String
)
