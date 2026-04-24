package models

import java.time.Instant

case class EmailVerificationCode(
  id: Option[Long] = None,
  userId: Long,
  email: String,
  code: String,
  createdAt: Instant = Instant.now(),
  expiresAt: Instant,
  verified: Boolean = false,
  attempts: Int = 0
) {
  def isExpired: Boolean = Instant.now().isAfter(expiresAt)
  def canAttempt: Boolean = attempts < 3 && !isExpired
}
