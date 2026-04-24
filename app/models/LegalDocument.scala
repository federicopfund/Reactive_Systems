package models

import java.time.Instant

/**
 * Documento legal versionable (privacidad, términos).
 * El cuerpo es HTML que la vista renderiza con `@Html(...)`.
 */
case class LegalDocument(
  id:            Option[Long] = None,
  slug:          String,
  title:         String,
  eyebrow:       String       = "Documento legal",
  bodyHtml:      String,
  lastUpdatedAt: Instant      = Instant.now(),
  isPublished:   Boolean      = true,
  createdAt:     Instant      = Instant.now()
)
