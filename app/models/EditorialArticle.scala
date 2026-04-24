package models

import java.time.Instant

/**
 * Pieza editorial fundacional del equipo de la edición.
 *
 * Diferencia con `Publication`:
 *   - Publication: contenido enviado por usuarios, con workflow editorial
 *   - EditorialArticle: pieza del equipo, siempre publicada, sin workflow
 *
 * El listado público (HomeController.publicaciones) muestra ambos.
 *
 * `bodyHtml` se renderiza tal cual con @Html(...). El contenido fue
 * migrado en evolution 24 desde app/views/articulos/ (archivos .scala.html).
 */
case class EditorialArticle(
  id:             Option[Long]   = None,
  slug:           String,
  title:          String,
  excerpt:        Option[String] = None,
  bodyHtml:       String,
  categoryId:     Option[Long]   = None,
  categoryLabel:  String,
  tagsPipe:       String         = "",
  publishedLabel: String,
  publishedAt:    Instant        = Instant.now(),
  coverImage:     Option[String] = None,
  isPublished:    Boolean        = true,
  viewCount:      Int            = 0,
  orderIndex:     Int            = 100,
  createdAt:      Instant        = Instant.now(),
  updatedAt:      Instant        = Instant.now()
) {
  def tags: Seq[String] =
    if (tagsPipe.isEmpty) Seq.empty
    else tagsPipe.split('|').toSeq.map(_.trim).filter(_.nonEmpty)
}
