package models

import java.time.Instant

/**
 * Snapshot numerado del contenido de una publicación.
 *
 * Cada vez que el autor incorpora cambios del feedback editorial y
 * entrega, se genera una nueva revisión. Se guarda el texto completo;
 * el diff contra versiones anteriores se calcula en aplicación.
 *
 * Toda publicación tiene al menos una revisión (v1), ya sea creada al
 * momento de la publicación o retroactivamente por el backfill del
 * Sprint 1 (evolution 19).
 *
 * Invariantes:
 *   - versionNumber > 0
 *   - Único por (publicationId, versionNumber) garantizado por BD.
 *
 * @param id             PK
 * @param publicationId  Publicación a la que pertenece esta versión
 * @param versionNumber  Entero monotónicamente creciente por publicación
 * @param title          Título en esta versión (puede cambiar entre revisiones)
 * @param content        Contenido completo en esta versión
 * @param excerpt        Extracto (opcional)
 * @param createdAt      Momento de creación
 * @param createdBy      Usuario que creó la revisión (normalmente el autor)
 * @param changeSummary  Breve descripción de qué se cambió ("Incorporé comentarios 3, 5 y 7")
 */
case class PublicationRevision(
  id: Option[Long] = None,
  publicationId: Long,
  versionNumber: Int,
  title: String,
  content: String,
  excerpt: Option[String] = None,
  createdAt: Instant = Instant.now(),
  createdBy: Option[Long] = None,
  changeSummary: Option[String] = None
) {
  require(versionNumber > 0, "versionNumber debe ser positivo")
}

/**
 * Par de versiones para comparación.
 * La comparación real (diff por líneas) se calcula en el servicio que
 * consume esto (Sprint 4, cuando se implemente la vista de diff).
 */
case class PublicationVersionPair(
  previous: PublicationRevision,
  current: PublicationRevision
)
