package utils

import models.{EditorialStage, EditorialStageCode}

/**
 * EditorialStagePolicy — quién puede mover una pieza fuera de cada etapa,
 * y a qué etapas puede llevarla.
 *
 * Fuente única de verdad para la UI (qué botones renderizar) y el server
 * (defensa en profundidad en `AdminController.transitionStage`).
 *
 * El mapeo de etapas vive en `editorial_stages` (DB, evolución 14) y aquí
 * se traduce `editorial_stages.required_role` a roles del backoffice
 * declarados en `RolePolicy`. Cambiar el dueño de una etapa = una línea
 * en este archivo, sin migración.
 */
object EditorialStagePolicy {

  import EditorialStageCode._

  /** Roles del backoffice habilitados para sacar una pieza de cada etapa. */
  private val ownerRoles: Map[String, Set[String]] = Map(
    Pitch              -> Set("editor_jefe", "super_admin"),
    Accepted           -> Set("editor_jefe", "super_admin"),         // re-asignar / cancelar
    InDraft            -> Set("editor_jefe", "super_admin"),         // mandar a copy si el autor delega
    InCopyEdit         -> Set("revisor", "editor_jefe", "super_admin"),
    InTechnicalReview  -> Set("revisor", "editor_jefe", "super_admin"),
    PendingApproval    -> Set("editor_jefe", "super_admin"),
    Scheduled          -> Set("editor_jefe", "super_admin"),
    Published          -> Set("editor_jefe", "super_admin"),         // despublicar / archivar
    Archived           -> Set.empty                                   // terminal estricto
  )

  /** Etiqueta humana para el dueño actual — usada en el banner readonly. */
  private val ownerLabels: Map[String, String] = Map(
    Pitch              -> "Editor en Jefe",
    Accepted           -> "Autor / Editor en Jefe",
    InDraft            -> "Autor / Editor en Jefe",
    InCopyEdit         -> "Revisor / Editor en Jefe",
    InTechnicalReview  -> "Revisor / Editor en Jefe",
    PendingApproval    -> "Editor en Jefe",
    Scheduled          -> "Editor en Jefe",
    Published          -> "Editor en Jefe",
    Archived           -> "—"
  )

  /**
   * Transiciones permitidas (sin rama de excepción).
   * Avance canónico + retornos editoriales habituales.
   */
  private val allowedTargets: Map[String, Set[String]] = Map(
    Pitch              -> Set(Accepted, Archived),
    Accepted           -> Set(InDraft, Archived),
    InDraft            -> Set(InCopyEdit, Archived),
    InCopyEdit         -> Set(InTechnicalReview, PendingApproval, InDraft, Archived),
    InTechnicalReview  -> Set(PendingApproval, InCopyEdit, Archived),
    PendingApproval    -> Set(Scheduled, Published, InCopyEdit, Archived),
    Scheduled          -> Set(Published, PendingApproval, Archived),
    Published          -> Set(Archived),
    Archived           -> Set.empty
  )

  /** ¿Puede `role` mover una pieza fuera de `fromCode`? */
  def canTransitionFrom(fromCode: String, role: String): Boolean =
    ownerRoles.getOrElse(fromCode, Set.empty).contains(role)

  /** Etiqueta del propietario actual (para el banner "Espera acción de…"). */
  def ownerLabelFor(stageCode: String): String =
    ownerLabels.getOrElse(stageCode, "—")

  /** ¿Es legal mover de `fromCode` a `toCode`? */
  def isAllowedTarget(fromCode: String, toCode: String): Boolean =
    allowedTargets.getOrElse(fromCode, Set.empty).contains(toCode)

  /**
   * Etapas a las que `role` puede mover una pieza desde `fromCode`.
   * Útil para construir los botones de transición en la vista.
   */
  def nextStagesFor(fromCode: String, role: String, allStages: Seq[EditorialStage]): Seq[EditorialStage] = {
    if (!canTransitionFrom(fromCode, role)) Seq.empty
    else {
      val targets = allowedTargets.getOrElse(fromCode, Set.empty)
      allStages.filter(s => targets.contains(s.code))
    }
  }
}
