package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.ExecutionContext

/**
 * ModerationEngine — Actor reactivo para moderación de contenido.
 *
 * Pipeline de moderación con auto-filtrado básico y cola de revisión:
 *   1. Recibe contenido para moderar (publicación, comentario)
 *   2. Ejecuta filtros automáticos (palabras prohibidas, spam)
 *   3. Marca como auto-aprobado o encolado para revisión manual
 *
 * Principios Reactivos:
 *   - Message-Driven: cada pieza de contenido es un mensaje en el pipeline
 *   - Resilient: fallos en filtros no bloquean el sistema
 *   - Responsive: respuesta inmediata con resultado preliminar
 */

// ── Commands ──
sealed trait ModerationCommand

case class ModerateContent(
  contentId: Long,
  contentType: String, // "publication", "comment"
  authorId: Long,
  title: Option[String],
  content: String,
  replyTo: ActorRef[ModerationResponse]
) extends ModerationCommand

case class UpdateBlocklist(
  words: Set[String]
) extends ModerationCommand

// ── Responses ──
sealed trait ModerationResponse
case class ModerationResult(
  contentId: Long,
  verdict: String, // "auto_approved", "pending_review", "auto_rejected"
  flags: List[String],
  score: Double
) extends ModerationResponse

object ModerationEngine {

  private val DEFAULT_BLOCKLIST: Set[String] = Set(
    "spam", "scam", "phishing", "malware"
  )

  def apply()(implicit ec: ExecutionContext): Behavior[ModerationCommand] =
    active(DEFAULT_BLOCKLIST)

  private def active(blocklist: Set[String])(implicit ec: ExecutionContext): Behavior[ModerationCommand] = {

    Behaviors.receive { (context, message) =>
      message match {

        case ModerateContent(contentId, contentType, authorId, title, content, replyTo) =>
          context.log.info(s"[ModerationEngine] Moderating $contentType #$contentId from user $authorId")

          val flags = scala.collection.mutable.ListBuffer[String]()
          var score = 0.0

          // ── Filter 1: Blocklist words ──
          val lowerContent = content.toLowerCase
          val titleLower = title.map(_.toLowerCase).getOrElse("")
          val foundBlocked = blocklist.filter(word => lowerContent.contains(word) || titleLower.contains(word))
          if (foundBlocked.nonEmpty) {
            flags += s"blocklist_match: ${foundBlocked.mkString(", ")}"
            score += foundBlocked.size * 30.0
          }

          // ── Filter 2: Excessive caps ──
          val capsRatio = content.count(_.isUpper).toDouble / content.length.max(1)
          if (capsRatio > 0.6 && content.length > 20) {
            flags += "excessive_caps"
            score += 15.0
          }

          // ── Filter 3: Repetitive characters ──
          val hasRepetitive = "(.)(\\1{4,})".r.findFirstIn(content).isDefined
          if (hasRepetitive) {
            flags += "repetitive_chars"
            score += 10.0
          }

          // ── Filter 4: Too short content ──
          if (content.length < 30 && contentType == "publication") {
            flags += "too_short"
            score += 20.0
          }

          // ── Filter 5: URL density ──
          val urlCount = "https?://\\S+".r.findAllIn(content).size
          if (urlCount > 3) {
            flags += s"high_url_density: $urlCount URLs"
            score += urlCount * 5.0
          }

          // ── Verdict ──
          val verdict = if (score >= 50.0) {
            "auto_rejected"
          } else if (score >= 20.0 || flags.nonEmpty) {
            "pending_review"
          } else {
            "auto_approved"
          }

          context.log.info(s"[ModerationEngine] $contentType #$contentId → $verdict (score: $score, flags: ${flags.size})")
          replyTo ! ModerationResult(contentId, verdict, flags.toList, score)
          Behaviors.same

        case UpdateBlocklist(words) =>
          context.log.info(s"[ModerationEngine] Blocklist updated with ${words.size} words")
          active(blocklist ++ words)
      }
    }
  }
}
