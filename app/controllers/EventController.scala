package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.{Json, JsObject}
import controllers.actions.{OptionalAuthAction, OptionalAuthRequest, AuthAction, AuthRequest}
import models.{CommunityEvent, EventEnums}
import repositories.{CommunityEventRepository, EventAttendeeRepository}
import services.ReactiveAnalyticsAdapter
import utils.IcsBuilder
import java.time.{Instant, LocalDate, ZoneId, YearMonth}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controlador publico de la Agenda Editorial — Issue #19.
 */
@Singleton
class EventController @Inject()(
  val controllerComponents: ControllerComponents,
  events: CommunityEventRepository,
  attendees: EventAttendeeRepository,
  analytics: ReactiveAnalyticsAdapter,
  optionalAuth: OptionalAuthAction,
  authAction: AuthAction
)(implicit ec: ExecutionContext) extends BaseController {

  private val DefaultZone = ZoneId.of("America/Argentina/Cordoba")

  private def baseUrl(implicit req: RequestHeader): String = {
    val scheme = if (req.secure) "https" else "http"
    s"$scheme://${req.host}"
  }

  // ── Vistas publicas ───────────────────────────────────────────────

  def index() = optionalAuth.async { implicit request =>
    analytics.trackPageView("/eventos", request.userInfo.map(_._1), request.headers.get("Referer"))
    val ym = YearMonth.now(DefaultZone)
    redirectToMonth(ym)
  }

  def month(ym: String) = optionalAuth.async { implicit request =>
    parseYearMonth(ym) match {
      case Some(yearMonth) =>
        val zone = DefaultZone
        val from = yearMonth.atDay(1).atStartOfDay(zone).toInstant
        val to   = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant
        for {
          monthEvents <- events.findPublishedByRange(from, to)
          upcoming    <- events.findPublishedUpcoming(limit = 5)
        } yield {
          val prev = yearMonth.minusMonths(1)
          val next = yearMonth.plusMonths(1)
          Ok(views.html.events.month(yearMonth, monthEvents, upcoming, prev, next, request.userInfo))
        }
      case None =>
        Future.successful(Redirect(routes.EventController.index()))
    }
  }

  def timeline() = optionalAuth.async { implicit request =>
    events.findPublishedAll(limit = 200).map { all =>
      val grouped: Seq[(String, Seq[CommunityEvent])] =
        all.groupBy(_.monthKey).toSeq.sortBy(_._1)
      Ok(views.html.events.timeline(grouped, request.userInfo))
    }
  }

  def past(page: Int) = optionalAuth.async { implicit request =>
    val pageSize = 20
    events.findPublishedPast(limit = pageSize, offset = page * pageSize).map { list =>
      Ok(views.html.events.past(list, page, pageSize, request.userInfo))
    }
  }

  def detail(slug: String) = optionalAuth.async { implicit request =>
    events.findBySlug(slug).flatMap {
      case Some(e) if e.status == EventEnums.EventStatus.Published || e.status == EventEnums.EventStatus.Cancelled =>
        e.id.foreach(events.incrementViewCount)
        analytics.trackPageView(s"/eventos/$slug", request.userInfo.map(_._1), request.headers.get("Referer"))
        val rsvpFut: Future[Option[models.EventAttendee]] = (request.userInfo, e.id) match {
          case (Some((uid, _, _)), Some(id)) => attendees.findForUserAndEvent(id, uid)
          case _                             => Future.successful(None)
        }
        for {
          rsvp     <- rsvpFut
          counts   <- e.id.map(attendees.countByStatus).getOrElse(Future.successful(Map.empty[String, Int]))
        } yield Ok(views.html.events.detail(e, rsvp, counts, request.userInfo))
      case _ =>
        Future.successful(NotFound(views.html.errors.notFound()))
    }
  }

  // ── ICS export ────────────────────────────────────────────────────

  def calendarIcs() = Action.async { implicit request =>
    events.findPublishedAll(limit = 500).map { list =>
      Ok(IcsBuilder.calendar(list, baseUrl))
        .as("text/calendar; charset=utf-8")
        .withHeaders("Content-Disposition" -> "attachment; filename=\"manifiesto-reactivo.ics\"")
    }
  }

  def singleIcs(slug: String) = Action.async { implicit request =>
    events.findBySlug(slug).map {
      case Some(e) =>
        Ok(IcsBuilder.single(e, baseUrl))
          .as("text/calendar; charset=utf-8")
          .withHeaders("Content-Disposition" -> s"attachment; filename=\"${e.slug}.ics\"")
      case None =>
        NotFound("Evento no encontrado")
    }
  }

  // ── RSVP (requiere usuario logueado) ──────────────────────────────

  def rsvp(slug: String) = authAction.async { implicit request =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val status = form.get("status").flatMap(_.headOption).getOrElse(EventEnums.RsvpStatus.Attending)
    val reminder = form.get("reminder").flatMap(_.headOption).contains("on")
    if (!EventEnums.RsvpStatus.all.contains(status)) {
      Future.successful(BadRequest(Json.obj("ok" -> false, "error" -> "RSVP invalido")))
    } else {
      events.findBySlug(slug).flatMap {
        case Some(e) if e.id.isDefined && e.status == EventEnums.EventStatus.Published =>
          attendees.upsertRsvp(e.id.get, request.userId, status, reminder).flatMap { _ =>
            attendees.countByStatus(e.id.get).map { counts =>
              if (request.headers.get("Accept").exists(_.contains("application/json"))) {
                Ok(Json.obj(
                  "ok"     -> true,
                  "status" -> status,
                  "counts" -> Json.toJson(counts)
                ))
              } else {
                Redirect(routes.EventController.detail(slug))
                  .flashing("success" -> s"RSVP registrado: ${EventEnums.RsvpStatus.label(status)}")
              }
            }
          }
        case _ =>
          Future.successful(NotFound(Json.obj("ok" -> false, "error" -> "Evento no encontrado")))
      }
    }
  }

  def toggleReminder(slug: String) = authAction.async { implicit request =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val optIn = form.get("optin").flatMap(_.headOption).contains("on")
    events.findBySlug(slug).flatMap {
      case Some(e) if e.id.isDefined =>
        attendees.toggleReminder(e.id.get, request.userId, optIn).map { _ =>
          Ok(Json.obj("ok" -> true, "optin" -> optIn))
        }
      case _ =>
        Future.successful(NotFound(Json.obj("ok" -> false)))
    }
  }

  // ── JSON APIs ─────────────────────────────────────────────────────

  def upcomingJson(limit: Int) = Action.async {
    events.findPublishedUpcoming(limit).map { list =>
      Ok(Json.obj("events" -> Json.toJson(list.map(serialize))))
    }
  }

  def calendarJson(from: Option[String], to: Option[String]) = Action.async {
    val zone = DefaultZone
    val now = Instant.now()
    val fromI = from.flatMap(s => parseDate(s)).map(_.atStartOfDay(zone).toInstant).getOrElse(now)
    val toI   = to.flatMap(s => parseDate(s)).map(_.plusDays(1).atStartOfDay(zone).toInstant).getOrElse(now.plusSeconds(60L * 24 * 3600))
    events.findPublishedByRange(fromI, toI).map { list =>
      Ok(Json.obj("events" -> Json.toJson(list.map(serialize))))
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────

  private def serialize(e: CommunityEvent): JsObject = Json.obj(
    "id"          -> e.id,
    "slug"        -> e.slug,
    "title"       -> e.title,
    "summary"     -> e.summary,
    "type"        -> e.eventType,
    "modality"    -> e.modality,
    "startsAt"    -> e.startsAt.toString,
    "endsAt"      -> e.endsAt.toString,
    "timezone"    -> e.timezone,
    "tags"        -> e.tags,
    "accent"      -> e.accentToken,
    "url"         -> s"/eventos/${e.slug}"
  )

  private def parseYearMonth(ym: String): Option[YearMonth] =
    scala.util.Try(YearMonth.parse(ym)).toOption

  private def parseDate(s: String): Option[LocalDate] =
    scala.util.Try(LocalDate.parse(s)).toOption

  private def redirectToMonth(ym: YearMonth)(implicit req: OptionalAuthRequest[AnyContent]): Future[Result] = {
    val key = f"${ym.getYear}%04d-${ym.getMonthValue}%02d"
    val zone = DefaultZone
    val from = ym.atDay(1).atStartOfDay(zone).toInstant
    val to   = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant
    for {
      monthEvents <- events.findPublishedByRange(from, to)
      upcoming    <- events.findPublishedUpcoming(limit = 5)
    } yield {
      val prev = ym.minusMonths(1)
      val next = ym.plusMonths(1)
      Ok(views.html.events.index(ym, monthEvents, upcoming, prev, next, req.userInfo))
    }
  }
}
