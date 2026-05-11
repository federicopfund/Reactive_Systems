package domains.contact.models

import java.time.Instant

case class ContactRecord(
  id: Option[Long] = None,
  name: String,
  email: String,
  message: String,
  createdAt: Instant = Instant.now(),
  status: String = "pending"
)
