package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.{Json, JsValue}
import controllers.actions.{AdminOnlyAction, AuthRequest, CapabilityCheck}
import models.{Collection, CollectionAccent, CollectionItemType, CollectionStatus, UserNotification}
import repositories.{CollectionRepository, UserNotificationRepository, UserRepository}
import utils.Capabilities.Cap
import utils.{CollectionWorkflowPolicy, RolePolicy}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Backoffice de colecciones editoriales — Issue #20.
 *
 * RBAC granular:
 *   - CollectionsCurate  -> ver, crear, editar metadatos, gestionar piezas, enviar a revisión
 *   - CollectionsReview  -> aprobar, devolver con cambios, regresar a borrador
 *   - CollectionsPublish -> publicar al portafolio, despublicar, archivar
 *
 * Toda transición pasa por `CollectionWorkflowPolicy` y por una transacción
 * en `CollectionRepository.transition` que garantiza historial atómico.
 */
@Singleton
class AdminCollectionController @Inject()(
  cc: ControllerComponents,
  collections: CollectionRepository,
  users: UserRepository,
  notifications: UserNotificationRepository,
  adminAction: AdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  // ───────────────────────── Listado ─────────────────────────

  def list(status: Option[String]) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.findAllForAdmin(status.filter(_.nonEmpty)).map { rows =>
        Ok(views.html.admin.collections.list(rows, status))
      }
    }
  }

  // ───────────────────────── Crear ─────────────────────────

  def newForm() = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      Future.successful(Ok(views.html.admin.collections.form(None, Map.empty, defaultFormData)))
    }
  }

  def create() = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      val data = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty))
      validate(data).flatMap {
        case Left(errors) =>
          Future.successful(BadRequest(views.html.admin.collections.form(None, errors, data)))
        case Right(coll) =>
          collections.slugExists(coll.slug, None).flatMap {
            case true =>
              Future.successful(BadRequest(views.html.admin.collections.form(
                None, Map("slug" -> "Ya existe una coleccion con ese slug"), data)))
            case false =>
              collections.create(coll, request.userId, request.role).map { id =>
                Redirect(routes.AdminCollectionController.view(id))
                  .flashing("success" -> "Coleccion creada en estado borrador.")
              }
          }
      }
    }
  }

  // ───────────────────────── Detalle ─────────────────────────

  def view(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.findById(id).flatMap {
        case None => Future.successful(NotFound("Coleccion no encontrada"))
        case Some(coll) =>
          for {
            items   <- collections.resolveItems(id)
            history <- collections.historyOf(id)
          } yield {
            val available = CollectionWorkflowPolicy.availableFor(request.role, coll.status)
            Ok(views.html.admin.collections.detail(coll, items, history, available))
          }
      }
    }
  }

  // ───────────────────────── Editar metadatos ─────────────────────────

  def editForm(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.findById(id).map {
        case None       => NotFound("Coleccion no encontrada")
        case Some(coll) =>
          if (!coll.isEditable)
            Redirect(routes.AdminCollectionController.view(id))
              .flashing("error" -> "Esta coleccion no es editable en su estado actual.")
          else
            Ok(views.html.admin.collections.form(Some(coll), Map.empty, toFormData(coll)))
      }
    }
  }

  def update(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.findById(id).flatMap {
        case None => Future.successful(NotFound("Coleccion no encontrada"))
        case Some(existing) if !existing.isEditable =>
          Future.successful(
            Redirect(routes.AdminCollectionController.view(id))
              .flashing("error" -> "Esta coleccion no se puede editar en su estado actual.")
          )
        case Some(existing) =>
          val data = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty))
          validate(data).flatMap {
            case Left(errors) =>
              Future.successful(BadRequest(views.html.admin.collections.form(Some(existing), errors, data)))
            case Right(parsed) =>
              collections.slugExists(parsed.slug, excludeId = Some(id)).flatMap {
                case true =>
                  Future.successful(BadRequest(views.html.admin.collections.form(
                    Some(existing), Map("slug" -> "Slug duplicado"), data)))
                case false =>
                  collections.updateMeta(
                    id           = id,
                    name         = parsed.name,
                    slug         = parsed.slug,
                    description  = parsed.description,
                    coverLabel   = parsed.coverLabel,
                    accentColor  = parsed.accentColor,
                    curatorId    = parsed.curatorId,
                    orderIndex   = parsed.orderIndex
                  ).map { _ =>
                    Redirect(routes.AdminCollectionController.view(id))
                      .flashing("success" -> "Coleccion actualizada.")
                  }
              }
          }
      }
    }
  }

  // ───────────────────────── Transiciones ─────────────────────────

  def transition(id: Long) = adminAction.async { implicit request =>
    val form    = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val action  = form.get("action").flatMap(_.headOption).getOrElse("").trim
    val comment = form.get("comment").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)

    collections.findById(id).flatMap {
      case None => Future.successful(NotFound("Coleccion no encontrada"))
      case Some(coll) =>
        CollectionWorkflowPolicy.canExecute(request.role, coll.status, action) match {
          case None =>
            Future.successful(
              Redirect(routes.AdminCollectionController.view(id))
                .flashing("error" -> "Transicion no permitida para tu rol.")
            )
          case Some(t) if t.requiresComment && comment.isEmpty =>
            Future.successful(
              Redirect(routes.AdminCollectionController.view(id))
                .flashing("error" -> s"La accion '${t.label}' requiere un comentario.")
            )
          case Some(t) =>
            CapabilityCheck.require(request, t.cap) {
              collections.transition(id, t.from, t.to, request.userId, request.role, comment).flatMap {
                case None =>
                  Future.successful(
                    Redirect(routes.AdminCollectionController.view(id))
                      .flashing("error" -> "El estado cambio en otra pestaña. Recarga e intentá de nuevo.")
                  )
                case Some(updated) =>
                  notifyTransition(updated, t, comment, request.userId).map { _ =>
                    Redirect(routes.AdminCollectionController.view(id))
                      .flashing("success" -> s"Estado actualizado: '${CollectionStatus.label(updated.status)}'.")
                  }
              }
            }
        }
    }
  }

  // ───────────────────────── Items ─────────────────────────

  def pickableItems(id: Long, q: String) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.pickable(id, q).map { rows =>
        val payload = Json.toJson(rows.map { r =>
          Json.obj(
            "type"   -> r.itemType,
            "id"     -> r.itemId,
            "title"  -> r.title,
            "slug"   -> r.slug,
            "author" -> r.author
          )
        })
        Ok(payload)
      }
    }
  }

  def addItem(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      val form   = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val tpe    = form.get("itemType").flatMap(_.headOption).getOrElse("")
      val itemId = form.get("itemId").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toLong).toOption)
      val note   = form.get("note").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)

      (CollectionItemType.all.contains(tpe), itemId) match {
        case (true, Some(iid)) =>
          collections.addItem(id, tpe, iid, note).map { _ =>
            Redirect(routes.AdminCollectionController.view(id))
              .flashing("success" -> "Pieza agregada a la coleccion.")
          }.recover { case _: Throwable =>
            Redirect(routes.AdminCollectionController.view(id))
              .flashing("error" -> "No se pudo agregar (puede que ya esté en la coleccion).")
          }
        case _ =>
          Future.successful(
            Redirect(routes.AdminCollectionController.view(id))
              .flashing("error" -> "Datos de pieza invalidos.")
          )
      }
    }
  }

  def removeItem(id: Long, itemRowId: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      collections.removeItem(itemRowId).map { _ =>
        Redirect(routes.AdminCollectionController.view(id))
          .flashing("success" -> "Pieza removida.")
      }
    }
  }

  def reorderItems(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.CollectionsCurate) {
      val ids = (request.body.asJson, request.body.asFormUrlEncoded) match {
        case (Some(json), _) =>
          (json \ "order").asOpt[Seq[Long]].getOrElse(Seq.empty)
        case (_, Some(form)) =>
          form.get("order[]").getOrElse(Seq.empty).flatMap(s => scala.util.Try(s.toLong).toOption)
        case _ => Seq.empty
      }
      if (ids.isEmpty)
        Future.successful(BadRequest(Json.obj("ok" -> false, "error" -> "Orden vacio")))
      else
        collections.reorderItems(id, ids).map(n => Ok(Json.obj("ok" -> true, "updated" -> n)))
    }
  }

  // ───────────────────────── Helpers ─────────────────────────

  private def parseForm(form: Map[String, Seq[String]]): Map[String, String] =
    form.view.mapValues(_.headOption.getOrElse("").trim).toMap

  private def defaultFormData: Map[String, String] = Map(
    "name"        -> "",
    "slug"        -> "",
    "description" -> "",
    "coverLabel"  -> "COLECCIÓN",
    "accentColor" -> CollectionAccent.Terracota,
    "curatorId"   -> "",
    "orderIndex"  -> "100"
  )

  private def toFormData(c: Collection): Map[String, String] = Map(
    "name"        -> c.name,
    "slug"        -> c.slug,
    "description" -> c.description.getOrElse(""),
    "coverLabel"  -> c.coverLabel,
    "accentColor" -> c.accentColor.getOrElse(CollectionAccent.Terracota),
    "curatorId"   -> c.curatorId.map(_.toString).getOrElse(""),
    "orderIndex"  -> c.orderIndex.toString
  )

  /**
   * Validacion liviana de los campos de la coleccion. Devuelve un Collection
   * SOLO con los campos editables (estado y auditoria los maneja el repo).
   */
  private def validate(d: Map[String, String]): Future[Either[Map[String, String], Collection]] = {
    val errors = scala.collection.mutable.Map.empty[String, String]
    val name        = d.getOrElse("name", "")
    val slug        = d.getOrElse("slug", "").toLowerCase
    val description = Option(d.getOrElse("description", "")).filter(_.nonEmpty)
    val coverLabel  = Option(d.getOrElse("coverLabel", "")).filter(_.nonEmpty).getOrElse("COLECCIÓN")
    val accentRaw   = d.getOrElse("accentColor", "")
    val curatorRaw  = d.getOrElse("curatorId", "")
    val orderRaw    = d.getOrElse("orderIndex", "100")

    if (name.length < 3 || name.length > 120) errors += ("name" -> "Nombre entre 3 y 120 caracteres.")
    val slugRe = """^[a-z0-9]+(?:-[a-z0-9]+)*$""".r
    if (!slugRe.matches(slug)) errors += ("slug" -> "Solo minusculas, numeros y guiones.")
    val accentColor = CollectionAccent.normalize(Some(accentRaw))
    if (accentRaw.nonEmpty && accentColor.isEmpty) errors += ("accentColor" -> "Color invalido.")
    val curatorId = if (curatorRaw.isEmpty) None
                    else scala.util.Try(curatorRaw.toLong).toOption.orElse {
                      errors += ("curatorId" -> "ID de curador invalido."); None
                    }
    val orderIndex = scala.util.Try(orderRaw.toInt).getOrElse {
      errors += ("orderIndex" -> "Orden debe ser numerico."); 100
    }

    if (errors.nonEmpty) Future.successful(Left(errors.toMap))
    else Future.successful(Right(Collection(
      slug         = slug,
      name         = name,
      description  = description,
      coverLabel   = coverLabel,
      curatorId    = curatorId,
      orderIndex   = orderIndex,
      accentColor  = accentColor
    )))
  }

  /**
   * Notifica a actores relevantes según la transición:
   *   - submit / back_to_review  -> revisores y publicadores
   *   - approve                  -> curador (createdBy) + publicadores
   *   - reject / back_to_draft   -> curador
   *   - publish                  -> curador
   */
  private def notifyTransition(
    coll: Collection, t: CollectionWorkflowPolicy.Transition,
    comment: Option[String], actorId: Long
  ): Future[Unit] = {
    val (notifType, title, msgPrefix, audience) = t.action match {
      case "submit" =>
        ("collection_submitted", s"Coleccion enviada a revision: ${coll.name}",
          "Una nueva coleccion espera revision.", AudienceReviewers)
      case "back_to_review" =>
        ("collection_submitted", s"Coleccion devuelta a revision: ${coll.name}",
          comment.getOrElse("Una coleccion volvio a la cola de revision."), AudienceReviewers)
      case "approve" =>
        ("collection_approved", s"Coleccion aprobada: ${coll.name}",
          comment.getOrElse("La coleccion fue aprobada y esta lista para publicar."),
          AudienceCuratorAndPublishers(coll.createdBy))
      case "reject" =>
        ("collection_rejected", s"Coleccion devuelta con cambios: ${coll.name}",
          comment.getOrElse("Se solicitaron cambios a la coleccion."),
          AudienceCurator(coll.createdBy))
      case "back_to_draft" =>
        ("collection_rejected", s"Coleccion vuelta a borrador: ${coll.name}",
          comment.getOrElse("La coleccion fue devuelta a borrador."),
          AudienceCurator(coll.createdBy))
      case "publish" =>
        ("collection_published", s"Coleccion publicada: ${coll.name}",
          "La coleccion ya es visible en el portafolio.",
          AudienceCurator(coll.createdBy))
      case _ =>
        ("collection_published", "", "", AudienceNone)
    }

    val targetIdsF: Future[Seq[Long]] = audience match {
      case AudienceNone =>
        Future.successful(Seq.empty)
      case AudienceCurator(uid) =>
        Future.successful(uid.filter(_ != actorId).toSeq)
      case AudienceReviewers =>
        users.findApprovedAdmins().map(_.collect {
          case u if u.id.exists(_ != actorId) &&
                    (RolePolicy.can(u.role, Cap.CollectionsReview) ||
                     RolePolicy.can(u.role, Cap.CollectionsPublish)) => u.id.get
        })
      case AudienceCuratorAndPublishers(curator) =>
        users.findApprovedAdmins().map { staff =>
          val publishers = staff.collect {
            case u if u.id.exists(_ != actorId) &&
                      RolePolicy.can(u.role, Cap.CollectionsPublish) => u.id.get
          }
          (curator.toSeq ++ publishers).distinct.filter(_ != actorId)
        }
    }

    targetIdsF.flatMap { ids =>
      if (ids.isEmpty || title.isEmpty) Future.successful(())
      else notifications.createBroadcast(ids, notifType, title, msgPrefix).map(_ => ())
    }
  }

  // Sumideros de audiencia para la matriz de notificación.
  private sealed trait Audience
  private case object AudienceNone extends Audience
  private case object AudienceReviewers extends Audience
  private case class  AudienceCurator(uid: Option[Long]) extends Audience
  private case class  AudienceCuratorAndPublishers(uid: Option[Long]) extends Audience
}
