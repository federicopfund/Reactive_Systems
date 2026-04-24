package utils

import models.CollectionStatus
import utils.Capabilities.Cap

/**
 * Política de workflow para colecciones editoriales (Issue #20).
 *
 * Define la matriz de transiciones permitidas en función de:
 *   - rol del actor
 *   - estado actual
 *   - acción solicitada (key textual: "submit", "approve", ...)
 *
 * Cada transición declara la capacidad necesaria y si el comentario es obligatorio.
 *
 * Esta clase es la fuente única de la verdad: el controller y la vista
 * deben consultarla antes de exponer botones o aceptar POSTs.
 */
object CollectionWorkflowPolicy {

  case class Transition(
    action:           String,
    label:            String,
    from:             String,
    to:               String,
    cap:              Cap,
    requiresComment:  Boolean = false,
    style:            String  = "primary"  // primary | success | warning | danger | ghost
  )

  // Matriz canónica. Si un rol no aparece para (from, action), no puede ejecutar la transición.
  // Se calcula primero por capacidad y luego se filtra por rol con RolePolicy.can.
  val all: Seq[Transition] = Seq(
    // Curador: del borrador al envío.
    Transition("submit",          "Enviar a revisión",     CollectionStatus.Draft,     CollectionStatus.InReview,  Cap.CollectionsCurate),
    Transition("retake",          "Retomar para edición",  CollectionStatus.Rejected,  CollectionStatus.Draft,     Cap.CollectionsCurate),
    Transition("revive",          "Reactivar como borrador", CollectionStatus.Archived, CollectionStatus.Draft,    Cap.CollectionsCurate),

    // Revisor: aprobar, rechazar, devolver a draft.
    Transition("approve",         "Aprobar",               CollectionStatus.InReview,  CollectionStatus.Approved,  Cap.CollectionsReview, requiresComment = false, style = "success"),
    Transition("reject",          "Devolver con cambios",  CollectionStatus.InReview,  CollectionStatus.Rejected,  Cap.CollectionsReview, requiresComment = true,  style = "warning"),
    Transition("back_to_draft",   "Volver a borrador",     CollectionStatus.InReview,  CollectionStatus.Draft,     Cap.CollectionsReview, style = "ghost"),

    // Publicador: lanzar y replegar.
    Transition("publish",         "Publicar al portafolio", CollectionStatus.Approved, CollectionStatus.Published, Cap.CollectionsPublish, style = "success"),
    Transition("back_to_review",  "Devolver a revisión",   CollectionStatus.Approved,  CollectionStatus.InReview,  Cap.CollectionsPublish, requiresComment = true, style = "ghost"),
    Transition("unpublish",       "Despublicar",           CollectionStatus.Published, CollectionStatus.Draft,     Cap.CollectionsPublish, style = "warning"),

    // Archivado: cualquiera con publish puede archivar; cualquiera con curate puede revivir.
    Transition("archive",         "Archivar",              CollectionStatus.Draft,     CollectionStatus.Archived,  Cap.CollectionsCurate, style = "ghost"),
    Transition("archive_rejected","Archivar",              CollectionStatus.Rejected,  CollectionStatus.Archived,  Cap.CollectionsCurate, style = "ghost"),
    Transition("archive_published","Archivar",             CollectionStatus.Published, CollectionStatus.Archived,  Cap.CollectionsPublish, style = "danger")
  )

  /** Encuentra una transición concreta por (from, action). */
  def find(from: String, action: String): Option[Transition] =
    all.find(t => t.from == from && t.action == action)

  /** Lista de transiciones disponibles para un rol y un estado dados. */
  def availableFor(role: String, from: String): Seq[Transition] =
    all.filter(t => t.from == from && RolePolicy.can(role, t.cap))

  /** ¿Puede este rol ejecutar esta transición? */
  def canExecute(role: String, from: String, action: String): Option[Transition] =
    find(from, action).filter(t => RolePolicy.can(role, t.cap))
}
