package domains.publications.models

import java.time.Instant

case class PublicationReaction(
  id: Option[Long] = None,
  publicationId: Long,
  userId: Long,
  reactionType: String = "like",
  createdAt: Instant = Instant.now()
)

object ReactionType {
  val Like      = "like"
  val Excellent = "excellent"
  val Learned   = "learned"

  val all = Seq(Like, Excellent, Learned)

  def emoji(rt: String): String = rt match {
    case Like      => "👍"
    case Excellent => "🔥"
    case Learned   => "💡"
    case _         => "👍"
  }

  def label(rt: String): String = rt match {
    case Like      => "Útil"
    case Excellent => "Excelente"
    case Learned   => "Aprendí algo"
    case _         => "Útil"
  }
}
