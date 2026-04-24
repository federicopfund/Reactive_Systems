package utils

/**
 * Sistema de roles + capacidades para el backoffice.
 *
 * - Los roles viven en `users.role` (string).
 * - La matriz de capacidades vive en este archivo (versionada en git,
 *   refactor seguro). Cualquier acción admin debe pasar por
 *   `RolePolicy.can(role, cap)` o por el `CapabilityAction` correspondiente.
 *
 * Diseño: ver propuesta "Roles editoriales granulares".
 */
object Capabilities {

  /** Capacidades discretas. Granularidad por acción, no por pantalla. */
  sealed trait Cap { def key: String; def label: String }

  object Cap {
    case object GovernanceManageAdmins extends Cap { val key = "governance.manage_admins"; val label = "Gestionar administradores" }

    case object PublicationsReview     extends Cap { val key = "publications.review";      val label = "Aprobar / rechazar publicaciones" }
    case object PublicationsDelete     extends Cap { val key = "publications.delete";      val label = "Eliminar publicaciones" }
    case object PublicationsEditNotes  extends Cap { val key = "publications.edit_notes";  val label = "Editar notas internas" }
    case object PublicationsFeedback   extends Cap { val key = "publications.feedback";    val label = "Crear y enviar feedback" }
    case object PublicationsExport     extends Cap { val key = "publications.export";      val label = "Exportar catálogo (JSON)" }

    case object ContactsView           extends Cap { val key = "contacts.view";            val label = "Ver bandeja de contacto" }
    case object ContactsEditStatus     extends Cap { val key = "contacts.edit_status";     val label = "Cambiar estado de contactos" }
    case object ContactsDelete         extends Cap { val key = "contacts.delete";          val label = "Eliminar contactos" }

    case object NewsletterView         extends Cap { val key = "newsletter.view";          val label = "Ver suscriptores" }
    case object NewsletterManage       extends Cap { val key = "newsletter.manage";        val label = "Gestionar newsletter" }

    case object StatsView              extends Cap { val key = "stats.view";               val label = "Ver estadísticas" }
    case object DashboardView          extends Cap { val key = "dashboard.view";           val label = "Acceder al backoffice" }

    case object EventsView             extends Cap { val key = "events.view";              val label = "Ver agenda editorial" }
    case object EventsManage           extends Cap { val key = "events.manage";            val label = "Crear y editar eventos" }
    case object EventsPublish          extends Cap { val key = "events.publish";           val label = "Publicar / cancelar eventos" }

    case object CollectionsCurate      extends Cap { val key = "collections.curate";       val label = "Curar colecciones (crear, editar piezas)" }
    case object CollectionsReview      extends Cap { val key = "collections.review";       val label = "Aprobar / devolver colecciones" }
    case object CollectionsPublish     extends Cap { val key = "collections.publish";      val label = "Publicar / archivar colecciones" }

    case object ObservabilityView      extends Cap { val key = "observability.view";      val label = "Ver dashboard de agentes y guardians" }
    case object ObservabilityManage    extends Cap { val key = "observability.manage";    val label = "Configurar variables de los agentes" }

    val all: Seq[Cap] = Seq(
      GovernanceManageAdmins,
      PublicationsReview, PublicationsDelete, PublicationsEditNotes,
      PublicationsFeedback, PublicationsExport,
      ContactsView, ContactsEditStatus, ContactsDelete,
      NewsletterView, NewsletterManage,
      StatsView, DashboardView,
      EventsView, EventsManage, EventsPublish,
      CollectionsCurate, CollectionsReview, CollectionsPublish,
      ObservabilityView, ObservabilityManage
    )
  }
}

/**
 * Catálogo de roles del backoffice + matriz de permisos.
 */
object RolePolicy {
  import Capabilities.Cap
  import Capabilities.Cap._

  /** Definición de un rol. */
  case class RoleDef(
    key: String,
    label: String,
    description: String,
    caps: Set[Cap]
  )

  /** Roles del backoffice. El orden es el orden visual en el selector. */
  val roles: List[RoleDef] = List(
    RoleDef(
      key = "super_admin",
      label = "Super Administrador",
      description = "Gobernanza total. Asigna roles, gestiona administradores y tiene acceso a todo el backoffice.",
      caps = Cap.all.toSet
    ),
    RoleDef(
      key = "editor_jefe",
      label = "Editor en Jefe",
      description = "Línea editorial. Aprobación final, edita notas, gestiona feedback y puede eliminar publicaciones.",
      caps = Set(
        DashboardView, StatsView,
        PublicationsReview, PublicationsDelete, PublicationsEditNotes,
        PublicationsFeedback, PublicationsExport,
        ContactsView,
        EventsView, EventsManage, EventsPublish,
        CollectionsCurate, CollectionsReview, CollectionsPublish
      )
    ),
    RoleDef(
      key = "revisor",
      label = "Revisor / Curador",
      description = "Curación de la cola de revisión. Aprueba, rechaza y deja feedback técnico. NO puede eliminar.",
      caps = Set(
        DashboardView, StatsView,
        PublicationsReview, PublicationsEditNotes, PublicationsFeedback, PublicationsExport,
        EventsView, EventsManage,
        CollectionsCurate, CollectionsReview
      )
    ),
    RoleDef(
      key = "moderador",
      label = "Moderador de Comunidad",
      description = "Bandeja de contactos: ver, cambiar estado y eliminar. Sin acceso a publicaciones ni newsletter.",
      caps = Set(
        DashboardView, StatsView,
        ContactsView, ContactsEditStatus, ContactsDelete,
        EventsView
      )
    ),
    RoleDef(
      key = "newsletter",
      label = "Encargado de Newsletter",
      description = "Gestiona suscriptores y campañas de newsletter. Sin acceso a curación ni gobernanza.",
      caps = Set(
        DashboardView, StatsView,
        NewsletterView, NewsletterManage
      )
    ),
    RoleDef(
      key = "analista",
      label = "Analista (Solo lectura)",
      description = "Solo lectura: dashboard, estadísticas, contactos, newsletter y exportes. Cero acciones destructivas.",
      caps = Set(
        DashboardView, StatsView,
        ContactsView, NewsletterView, PublicationsExport,
        EventsView
      )
    )
  )

  // Compat: el rol legacy "admin" mapea al rol más cercano (Editor en Jefe)
  // hasta que el super_admin reasigne explícitamente. Nunca aparece como
  // opción en el selector.
  private val legacyAdmin: RoleDef = roles.find(_.key == "editor_jefe").get.copy(key = "admin", label = "Administrador (legacy)")

  private val byKey: Map[String, RoleDef] =
    roles.map(r => r.key -> r).toMap + ("admin" -> legacyAdmin)

  /** Roles válidos como destino de asignación (excluye user / admin legacy). */
  val assignableRoles: List[RoleDef] = roles

  /** Set de claves consideradas "staff del backoffice" (cualquier acceso al admin layout). */
  val backofficeRoleKeys: Set[String] = roles.map(_.key).toSet + "admin"

  def isBackoffice(role: String): Boolean = backofficeRoleKeys.contains(role)

  def get(role: String): Option[RoleDef] = byKey.get(role)

  def labelFor(role: String): String = byKey.get(role).map(_.label).getOrElse(role)

  def descriptionFor(role: String): String = byKey.get(role).map(_.description).getOrElse("")

  def can(role: String, cap: Capabilities.Cap): Boolean =
    byKey.get(role).exists(_.caps.contains(cap))

  /** Lista de capacidades de un rol — útil para mostrar en notificaciones / UI. */
  def capsOf(role: String): Set[Capabilities.Cap] =
    byKey.get(role).map(_.caps).getOrElse(Set.empty)
}
