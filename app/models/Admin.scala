package models

import java.time.Instant

case class Admin(
  id: Option[Long] = None,
  username: String,
  email: String,
  passwordHash: String,
  role: String = "admin",
  createdAt: Instant = Instant.now(),
  lastLogin: Option[Instant] = None
)
