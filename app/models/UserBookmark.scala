package models

import java.time.Instant

case class UserBookmark(
  id: Option[Long] = None,
  userId: Long,
  publicationId: Long,
  createdAt: Instant = Instant.now()
)
