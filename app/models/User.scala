package models

import java.time.Instant

case class User(
  id: Option[Long] = None,
  username: String,
  email: String,
  passwordHash: String,
  fullName: String,
  role: String = "user",           // user | admin | super_admin | pending_admin
  isActive: Boolean = true,
  createdAt: Instant = Instant.now(),
  lastLogin: Option[Instant] = None,
  emailVerified: Boolean = false,
  bio: String = "",
  avatarUrl: String = "",
  website: String = "",
  location: String = "",
  adminApproved: Boolean = false,
  adminApprovedBy: Option[Long] = None,
  adminRequestedAt: Option[Instant] = None
) {
  def isSuperAdmin: Boolean = role == "super_admin"
  def isAdmin: Boolean      = role == "admin" || role == "super_admin"
  def isPendingAdmin: Boolean = role == "pending_admin"
  def isAdminRole: Boolean  = isAdmin || isPendingAdmin
}
