package models

import java.time.Instant

/**
 * Categoría dentro de la taxonomía editorial. Antes era una lista
 * literal en publicaciones.scala.html; ahora vive en DB.
 *
 * Compartida por publications y editorial_articles.
 */
case class PublicationCategory(
  id:          Option[Long]   = None,
  slug:        String,
  name:        String,
  description: Option[String] = None,
  iconEmoji:   Option[String] = None,
  orderIndex:  Int            = 100,
  active:      Boolean        = true,
  createdAt:   Instant        = Instant.now()
)
