package domains.publications.engines.publication

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del PublicationEngine.
 *
 * Dos niveles de visibilidad:
 *   - Comandos públicos  → API externa (adapters, pipelines).
 *   - Comandos internos  → `private[engines]`, usados solo por el actor
 *                          para encadenar resultados vía `pipeToSelf`.
 */
sealed trait PublicationCommand

// ── API pública ────────────────────────────────────────────────────────────

case class CreatePublication(
  userId:     Long,
  username:   String,
  title:      String,
  content:    String,
  excerpt:    Option[String],
  coverImage: Option[String],
  category:   String,
  tags:       Option[String],
  replyTo:    ActorRef[PublicationResponse]
) extends PublicationCommand

case class ApprovePublication(
  publicationId: Long,
  adminId:       Long,
  adminUsername: String,
  replyTo:       ActorRef[PublicationResponse]
) extends PublicationCommand

case class RejectPublication(
  publicationId: Long,
  adminId:       Long,
  adminUsername: String,
  reason:        String,
  replyTo:       ActorRef[PublicationResponse]
) extends PublicationCommand

// ── Protocolo interno (visible solo dentro del package engines) ────────────

private[engines] case class PublicationCreated(
  publicationId: Long,
  username:      String,
  replyTo:       ActorRef[PublicationResponse]
) extends PublicationCommand

private[engines] case class PublicationCreateFailed(
  exception: Throwable,
  replyTo:   ActorRef[PublicationResponse]
) extends PublicationCommand

private[engines] case class PublicationStatusUpdated(
  publicationId: Long,
  newStatus:     String,
  userId:        Long,
  adminUsername: String,
  reason:        Option[String],
  replyTo:       ActorRef[PublicationResponse]
) extends PublicationCommand

private[engines] case class PublicationStatusUpdateFailed(
  exception: Throwable,
  replyTo:   ActorRef[PublicationResponse]
) extends PublicationCommand

private[engines] case class PublicationNotified(
  publicationId: Long
) extends PublicationCommand

private[engines] case class PublicationNotifyFailed(
  exception:     Throwable,
  publicationId: Long
) extends PublicationCommand
