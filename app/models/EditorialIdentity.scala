package models

import java.time.Instant

/**
 * Bloque de identidad editorial (Issue #21).
 * Cada fila es uno de: mission, thesis, promise, audience_primary,
 * audience_secondary, we_are_not.
 *
 * `bodyHtml` se renderiza tal cual desde la DB (patrón LegalDocument).
 */
case class EditorialIdentity(
  id:         Option[Long] = None,
  sectionKey: String,
  title:      String,
  bodyHtml:   String,
  orderIndex: Int          = 100,
  active:     Boolean      = true,
  updatedAt:  Instant      = Instant.now()
)

object EditorialIdentity {
  // Claves canónicas — usadas por la vista pública para layout específico
  // (mission/thesis/promise/audiencias/we_are_not se renderizan distinto).
  val KeyMission           = "mission"
  val KeyThesis            = "thesis"
  val KeyPromise           = "promise"
  val KeyAudiencePrimary   = "audience_primary"
  val KeyAudienceSecondary = "audience_secondary"
  val KeyWeAreNot          = "we_are_not"

  val AllKeys: Seq[String] = Seq(
    KeyMission, KeyThesis, KeyPromise,
    KeyAudiencePrimary, KeyAudienceSecondary, KeyWeAreNot
  )
}
