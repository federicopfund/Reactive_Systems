package controllers

import javax.inject._
import play.api.mvc._
import controllers.actions.{AdminOnlyAction, AuthRequest, CapabilityCheck}
import models.{CommunityEvent, EventEnums, EventSpeaker}
import repositories.{CommunityEventRepository, EventAttendeeRepository, UserRepository}
import utils.Capabilities.Cap
import play.api.libs.json.Json
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Backoffice de eventos — Issue #19.
 *
 * RBAC:
 *   - EventsView    -> listar / ver detalle / asistentes
 *   - EventsManage  -> crear / editar / eliminar borradores
 *   - EventsPublish -> cambiar estado (publicar / cancelar / archivar)
 */
@Singleton
class AdminEventController @Inject()(
  cc: ControllerComponents,
  events: CommunityEventRepository,
  attendeeRepo: EventAttendeeRepository,
  users: UserRepository,
  adminAction: AdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val DefaultZone = ZoneId.of("America/Argentina/Cordoba")

  // ── Listado ──────────────────────────────────────────────────────
  def list(status: Option[String], page: Int) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsView) {
      val pageSize = 20
      for {
        list   <- events.findAllForAdmin(status.filter(_.nonEmpty), page, pageSize)
        total  <- events.countForAdmin(status.filter(_.nonEmpty))
        counts <- events.countByStatus()
      } yield Ok(views.html.admin.events.list(list, status, page, pageSize, total, counts))
    }
  }

  // ── Crear ────────────────────────────────────────────────────────
  def newForm() = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsManage) {
      Future.successful(Ok(views.html.admin.events.form(None, formErrors = Map.empty, formData = defaultFormData)))
    }
  }

  def create() = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsManage) {
      val data = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty))
      validate(data).flatMap {
        case Left(errors) =>
          Future.successful(BadRequest(views.html.admin.events.form(None, errors, data)))
        case Right(event) =>
          val toPersist = event.copy(createdBy = request.userId)
          events.slugExists(event.slug).flatMap {
            case true =>
              Future.successful(BadRequest(views.html.admin.events.form(None, Map("slug" -> "Ya existe un evento con ese slug"), data)))
            case false =>
              events.create(toPersist).map { id =>
                Redirect(routes.AdminEventController.view(id)).flashing("success" -> "Evento creado.")
              }
          }
      }
    }
  }

  // ── Ver ──────────────────────────────────────────────────────────
  def view(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsView) {
      events.findById(id).flatMap {
        case Some(e) =>
          for {
            counts <- attendeeRepo.countByStatus(id)
          } yield Ok(views.html.admin.events.view(e, counts))
        case None => Future.successful(NotFound("Evento no encontrado"))
      }
    }
  }

  // ── Editar ───────────────────────────────────────────────────────
  def editForm(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsManage) {
      events.findById(id).map {
        case Some(e) => Ok(views.html.admin.events.form(Some(e), Map.empty, toFormData(e)))
        case None    => NotFound("Evento no encontrado")
      }
    }
  }

  def update(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsManage) {
      events.findById(id).flatMap {
        case Some(existing) =>
          val data = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty))
          validate(data).flatMap {
            case Left(errors) =>
              Future.successful(BadRequest(views.html.admin.events.form(Some(existing), errors, data)))
            case Right(parsed) =>
              events.slugExists(parsed.slug, excluding = Some(id)).flatMap {
                case true =>
                  Future.successful(BadRequest(views.html.admin.events.form(Some(existing), Map("slug" -> "Slug duplicado"), data)))
                case false =>
                  val merged = existing.copy(
                    slug = parsed.slug,
                    title = parsed.title,
                    summary = parsed.summary,
                    descriptionHtml = parsed.descriptionHtml,
                    eventType = parsed.eventType,
                    modality = parsed.modality,
                    startsAt = parsed.startsAt,
                    endsAt = parsed.endsAt,
                    timezone = parsed.timezone,
                    locationName = parsed.locationName,
                    locationUrl = parsed.locationUrl,
                    locationDetail = parsed.locationDetail,
                    coverImage = parsed.coverImage,
                    accentColor = parsed.accentColor,
                    capacity = parsed.capacity,
                    tagsPipe = parsed.tagsPipe,
                    speakersJson = parsed.speakersJson
                  )
                  events.update(merged).map { _ =>
                    Redirect(routes.AdminEventController.view(id)).flashing("success" -> "Evento actualizado.")
                  }
              }
          }
        case None => Future.successful(NotFound("Evento no encontrado"))
      }
    }
  }

  // ── Cambio de estado ─────────────────────────────────────────────
  def changeStatus(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsPublish) {
      val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val newStatus = form.get("status").flatMap(_.headOption).getOrElse("")
      val reason    = form.get("reason").flatMap(_.headOption).filter(_.nonEmpty)
      events.changeStatus(id, newStatus, request.userId, reason).map { n =>
        if (n > 0) Redirect(routes.AdminEventController.view(id))
          .flashing("success" -> s"Estado actualizado a '${EventEnums.EventStatus.label(newStatus)}'.")
        else Redirect(routes.AdminEventController.view(id))
          .flashing("error" -> "Transicion de estado no permitida.")
      }
    }
  }

  // ── Eliminar ─────────────────────────────────────────────────────
  def delete(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsManage) {
      events.findById(id).flatMap {
        case Some(e) if e.status == EventEnums.EventStatus.Draft =>
          events.delete(id).map { _ =>
            Redirect(routes.AdminEventController.list(None, 0))
              .flashing("success" -> s"Evento '${e.title}' eliminado.")
          }
        case Some(_) =>
          Future.successful(Redirect(routes.AdminEventController.view(id))
            .flashing("error" -> "Solo borradores pueden eliminarse. Archivalo en su lugar."))
        case None =>
          Future.successful(NotFound("Evento no encontrado"))
      }
    }
  }

  // ── Asistentes ───────────────────────────────────────────────────
  def attendees(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsView) {
      events.findById(id).flatMap {
        case Some(e) =>
          for {
            list <- attendeeRepo.findByEvent(id)
            usersById <- {
              val ids = list.map(_.userId).distinct
              Future.sequence(ids.map(uid => users.findById(uid).map(uid -> _))).map(_.toMap)
            }
            counts <- attendeeRepo.countByStatus(id)
          } yield Ok(views.html.admin.events.attendees(e, list, usersById, counts))
        case None => Future.successful(NotFound("Evento no encontrado"))
      }
    }
  }

  def attendeesCsv(id: Long) = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.EventsView) {
      events.findById(id).flatMap {
        case Some(e) =>
          attendeeRepo.findByEvent(id).flatMap { list =>
            val ids = list.map(_.userId).distinct
            Future.sequence(ids.map(uid => users.findById(uid).map(uid -> _))).map(_.toMap).map { usersById =>
              val header = "user_id,username,email,rsvp_status,reminder_optin,created_at"
              val rows = list.map { a =>
                val u = usersById.get(a.userId).flatten
                val username = u.map(_.username).getOrElse("")
                val email    = u.map(_.email).getOrElse("")
                Seq(a.userId, csv(username), csv(email), a.rsvpStatus, a.reminderOptin, a.createdAt).mkString(",")
              }
              val body = (header +: rows).mkString("\n") + "\n"
              Ok(body).as("text/csv; charset=utf-8")
                .withHeaders("Content-Disposition" -> s"attachment; filename=\"asistentes-${e.slug}.csv\"")
            }
          }
        case None => Future.successful(NotFound("Evento no encontrado"))
      }
    }
  }

  // ── Helpers de form ──────────────────────────────────────────────

  private val defaultFields: Map[String, String] = Map(
    "slug" -> "", "title" -> "", "summary" -> "", "descriptionHtml" -> "",
    "eventType" -> EventEnums.EventType.Talk, "modality" -> EventEnums.Modality.Online,
    "startsAtLocal" -> "", "endsAtLocal" -> "",
    "timezone" -> "America/Argentina/Cordoba",
    "locationName" -> "", "locationUrl" -> "", "locationDetail" -> "",
    "coverImage" -> "", "accentColor" -> "",
    "capacity" -> "", "tagsPipe" -> "", "speakersJson" -> "[]"
  )

  private val defaultFormData: Map[String, String] = defaultFields

  private def parseForm(form: Map[String, Seq[String]]): Map[String, String] = {
    def s(k: String) = form.get(k).flatMap(_.headOption).map(_.trim).getOrElse("")
    defaultFields.keys.map { k =>
      val v = s(k)
      k -> (if (v.isEmpty) defaultFields(k) else v)
    }.toMap
  }

  private def toFormData(e: CommunityEvent): Map[String, String] = {
    val zone = scala.util.Try(ZoneId.of(e.timezone)).getOrElse(DefaultZone)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    Map(
      "slug" -> e.slug, "title" -> e.title,
      "summary" -> e.summary.getOrElse(""), "descriptionHtml" -> e.descriptionHtml,
      "eventType" -> e.eventType, "modality" -> e.modality,
      "startsAtLocal" -> e.startsAt.atZone(zone).toLocalDateTime.format(fmt),
      "endsAtLocal"   -> e.endsAt.atZone(zone).toLocalDateTime.format(fmt),
      "timezone" -> e.timezone,
      "locationName" -> e.locationName.getOrElse(""),
      "locationUrl" -> e.locationUrl.getOrElse(""),
      "locationDetail" -> e.locationDetail.getOrElse(""),
      "coverImage" -> e.coverImage.getOrElse(""),
      "accentColor" -> e.accentColor.getOrElse(""),
      "capacity" -> e.capacity.map(_.toString).getOrElse(""),
      "tagsPipe" -> e.tagsPipe,
      "speakersJson" -> (if (e.speakersJson.isEmpty) "[]" else e.speakersJson)
    )
  }

  private def validate(d: Map[String, String]): Future[Either[Map[String, String], CommunityEvent]] = Future.successful {
    val errors = scala.collection.mutable.Map.empty[String, String]
    val slug = d("slug"); val title = d("title"); val descriptionHtml = d("descriptionHtml")
    val eventType = d("eventType"); val modality = d("modality")
    val timezone = d("timezone")
    val startsAtLocal = d("startsAtLocal"); val endsAtLocal = d("endsAtLocal")
    val capacity = d("capacity")
    val speakersJsonRaw = d("speakersJson")

    if (slug.isEmpty || !slug.matches("^[a-z0-9-]+$")) errors += "slug" -> "Slug requerido (a-z 0-9 -)."
    if (title.isEmpty) errors += "title" -> "Titulo requerido."
    if (descriptionHtml.isEmpty) errors += "descriptionHtml" -> "Descripcion requerida."
    if (!EventEnums.EventType.all.contains(eventType)) errors += "eventType" -> "Tipo invalido."
    if (!EventEnums.Modality.all.contains(modality)) errors += "modality" -> "Modalidad invalida."

    val zone = scala.util.Try(ZoneId.of(timezone)).getOrElse(DefaultZone)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val startInst = scala.util.Try(LocalDateTime.parse(startsAtLocal, fmt).atZone(zone).toInstant).toOption
    val endInst   = scala.util.Try(LocalDateTime.parse(endsAtLocal, fmt).atZone(zone).toInstant).toOption
    if (startInst.isEmpty) errors += "startsAtLocal" -> "Fecha/hora de inicio invalida."
    if (endInst.isEmpty)   errors += "endsAtLocal"   -> "Fecha/hora de fin invalida."
    for (s <- startInst; e <- endInst if !e.isAfter(s)) errors += "endsAtLocal" -> "Fin debe ser posterior al inicio."

    val capacityOpt = if (capacity.isEmpty) None else scala.util.Try(capacity.toInt).toOption
    if (capacity.nonEmpty && capacityOpt.isEmpty) errors += "capacity" -> "Capacidad debe ser numerica."

    val parsedSpeakers = scala.util.Try(EventSpeaker.parseAll(speakersJsonRaw)).getOrElse(Seq.empty)
    val speakersJsonOk = scala.util.Try(play.api.libs.json.Json.parse(speakersJsonRaw)).isSuccess
    if (!speakersJsonOk) errors += "speakersJson" -> "JSON de oradores invalido."

    if (errors.nonEmpty) Left(errors.toMap)
    else {
      val ev = CommunityEvent(
        id = None,
        slug = slug,
        title = title,
        summary = Option(d("summary")).filter(_.nonEmpty),
        descriptionHtml = descriptionHtml,
        eventType = eventType,
        modality = modality,
        startsAt = startInst.get,
        endsAt = endInst.get,
        timezone = timezone,
        locationName = Option(d("locationName")).filter(_.nonEmpty),
        locationUrl = Option(d("locationUrl")).filter(_.nonEmpty),
        locationDetail = Option(d("locationDetail")).filter(_.nonEmpty),
        coverImage = Option(d("coverImage")).filter(_.nonEmpty),
        accentColor = Option(d("accentColor")).filter(_.nonEmpty),
        capacity = capacityOpt,
        tagsPipe = d("tagsPipe"),
        speakersJson = EventSpeaker.writeAll(parsedSpeakers),
        status = EventEnums.EventStatus.Draft,
        createdBy = 0L
      )
      Right(ev)
    }
  }

  private def csv(s: String): String = {
    val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n')
    if (needsQuote) "\"" + s.replace("\"", "\"\"") + "\"" else s
  }
}
