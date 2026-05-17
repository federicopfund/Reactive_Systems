package shared.moderation

import akka.actor.typed.ActorRef

/**
 * Protocolo de comandos del ModerationEngine.
 *
 * Todos los comandos son públicos.
 * El pipeline de filtros es síncrono (no usa pipeToSelf).
 */
sealed trait ModerationCommand

case class ModerateContent(
  contentId:   Long,
  contentType: String, // "publication", "comment"
  authorId:    Long,
  title:       Option[String],
  content:     String,
  replyTo:     ActorRef[ModerationResponse]
) extends ModerationCommand

case class UpdateBlocklist(
  words: Set[String]
) extends ModerationCommand
