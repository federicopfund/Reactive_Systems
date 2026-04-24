package models

import java.time.Instant

/**
 * Pilar del Manifiesto Reactivo (tesis fundacional).
 *
 * El campo `tagsPipe` almacena los tags separados por '|' para mantener
 * portabilidad SQL. La vista los splittea para renderizar.
 *
 * `accentColor` referencia un token del design system
 * (responsive | resilient | elastic | message).
 */
case class ManifestoPillar(
  id:           Option[Long] = None,
  pillarNumber: Int,
  romanNumeral: String,
  name:         String,
  description:  String,
  tagsPipe:     String         = "",
  accentColor:  Option[String] = None,
  orderIndex:   Int            = 100,
  active:       Boolean        = true,
  createdAt:    Instant        = Instant.now(),
  updatedAt:    Instant        = Instant.now()
) {
  def tags: Seq[String] =
    if (tagsPipe.isEmpty) Seq.empty
    else tagsPipe.split('|').toSeq.map(_.trim).filter(_.nonEmpty)
}
