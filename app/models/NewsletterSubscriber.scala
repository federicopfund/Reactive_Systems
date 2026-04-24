package models

import java.time.Instant

case class NewsletterSubscriber(
  id: Option[Long] = None,
  email: String,
  subscribedAt: Instant = Instant.now(),
  isActive: Boolean = true,
  unsubscribedAt: Option[Instant] = None,
  ipAddress: Option[String] = None
)
