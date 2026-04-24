package models

import java.time.Instant

/**
 * Estados del workflow editorial de una coleccion (Issue #20).
 *
 * Flujo:
 *   draft  -submit->  in_review  -approve->  approved  -publish->  published
 *   draft  -archive->  archived
 *   in_review  -reject->  rejected   (con comentario obligatorio)
 *   in_review  -back_to_draft->  draft
 *   approved   -back_to_review->  in_review (con comentario)
 *   rejected   -retake->  draft
 *   rejected   -archive->  archived
 *   published  -unpublish->  draft
 *   published  -archive->  archived
 *   archived   -revive->  draft
 */
object CollectionStatus {
  val Draft     = "draft"
  val InReview  = "in_review"
  val Approved  = "approved"
  val Rejected  = "rejected"
  val Published = "published"
  val Archived  = "archived"

  val all: Seq[String] = Seq(Draft, InReview, Approved, Rejected, Published, Archived)

  def label(status: String): String = status match {
    case Draft     => "Borrador"
    case InReview  => "En revision"
    case Approved  => "Aprobada"
    case Rejected  => "Devuelta"
    case Published => "Publicada"
    case Archived  => "Archivada"
    case other     => other
  }

  /** Token visual para badges. */
  def tone(status: String): String = status match {
    case Draft     => "draft"
    case InReview  => "review"
    case Approved  => "approved"
    case Rejected  => "rejected"
    case Published => "published"
    case Archived  => "archived"
    case _         => "neutral"
  }

  def isValid(status: String): Boolean = all.contains(status)
}

/**
 * Tokens de color disponibles para personalizar el acento visual de una coleccion.
 * Coordinados con _collections-public.scss.
 */
object CollectionAccent {
  val Terracota = "terracota"
  val Ambar     = "ambar"
  val Oliva     = "oliva"
  val Indigo    = "indigo"
  val Bordo     = "bordo"
  val Grafito   = "grafito"

  val all: Seq[String] = Seq(Terracota, Ambar, Oliva, Indigo, Bordo, Grafito)

  def label(token: String): String = token match {
    case Terracota => "Terracota"
    case Ambar     => "Ambar"
    case Oliva     => "Oliva"
    case Indigo    => "Indigo"
    case Bordo     => "Bordo"
    case Grafito   => "Grafito"
    case other     => other
  }

  def isValid(token: String): Boolean = all.contains(token)

  def normalize(opt: Option[String]): Option[String] =
    opt.map(_.trim.toLowerCase).filter(isValid)
}

/**
 * Coleccion temática curada por un editor.
 * Issue #20: workflow editorial con estados, auditoria y notas de revision.
 */
case class Collection(
  id:           Option[Long]    = None,
  slug:         String,
  name:         String,
  description:  Option[String]  = None,
  coverLabel:   String          = "COLECCIÓN",
  curatorId:    Option[Long]    = None,
  isPublished:  Boolean         = false,
  orderIndex:   Int             = 100,
  createdAt:    Instant         = Instant.now(),
  updatedAt:    Instant         = Instant.now(),
  // ── Workflow (Issue #20) ──
  status:       String          = CollectionStatus.Draft,
  createdBy:    Option[Long]    = None,
  submittedAt:  Option[Instant] = None,
  reviewedBy:   Option[Long]    = None,
  reviewedAt:   Option[Instant] = None,
  publishedBy:  Option[Long]    = None,
  publishedAt:  Option[Instant] = None,
  reviewNotes:  Option[String]  = None,
  accentColor:  Option[String]  = None
) {
  /** Visible en el portafolio publico. */
  def isLive: Boolean = status == CollectionStatus.Published && isPublished

  /** El curador puede modificar metadatos / piezas. */
  def isEditable: Boolean =
    status == CollectionStatus.Draft || status == CollectionStatus.Rejected

  /** Token de color resuelto para la UI (con fallback). */
  def accent: String =
    accentColor.filter(CollectionAccent.isValid).getOrElse(CollectionAccent.Terracota)
}

object CollectionItemType {
  val Publication      = "publication"
  val EditorialArticle = "editorial_article"
  val all: Set[String] = Set(Publication, EditorialArticle)

  def label(t: String): String = t match {
    case Publication      => "Publicacion"
    case EditorialArticle => "Articulo editorial"
    case other            => other
  }
}

case class CollectionItem(
  id:           Option[Long]    = None,
  collectionId: Long,
  itemType:     String,
  itemId:       Long,
  orderIndex:   Int             = 100,
  addedAt:      Instant         = Instant.now(),
  curatorNote:  Option[String]  = None
)

/** Coleccion con conteo de piezas (para listados/portafolio). */
case class CollectionWithCount(
  collection: Collection,
  itemCount:  Int
)

/** Entrada del historial de transiciones. */
case class CollectionStatusEntry(
  id:           Option[Long]    = None,
  collectionId: Long,
  fromStatus:   Option[String],
  toStatus:     String,
  actorId:      Option[Long],
  actorRole:    Option[String],
  comment:      Option[String],
  createdAt:    Instant         = Instant.now()
)

/**
 * Item resuelto: una pieza con metadata visible (titulo / slug / autor)
 * mas el flag `existing` que es false si la fuente fue eliminada.
 */
case class ResolvedCollectionItem(
  item:     CollectionItem,
  title:    String,
  slug:     String,
  author:   String,
  existing: Boolean
)

/** Item ofrecido al curador en el buscador (publicacion aprobada o articulo editorial). */
case class PickableItem(
  itemType: String,
  itemId:   Long,
  title:    String,
  slug:     String,
  author:   String
)
