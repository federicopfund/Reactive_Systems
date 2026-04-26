package controllers

import controllers.actions.{AdminOnlyAction, CapabilityCheck}
import models.EditorialSeason
import play.api.mvc._
import repositories.EditorialSeasonRepository
import repositories.{NewsletterRepository, UserNotificationRepository, UserRepository}
import utils.Capabilities.Cap

import javax.inject._
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging

private[controllers] object AdminSeasonValidation {
  case class Parsed(
    code: String,
    name: String,
    tagline: Option[String],
    openingEssay: Option[String],
    startsOn: Option[LocalDate],
    endsOn: Option[LocalDate]
  )

  def parseForm(form: Map[String, Seq[String]]): Map[String, String] = {
    def s(k: String): String = form.get(k).flatMap(_.headOption).map(_.trim).getOrElse("")
    Seq("code", "name", "tagline", "openingEssay", "startsOn", "endsOn").map(k => k -> s(k)).toMap
  }

  def shouldAnnounceNewsletter(form: Map[String, Seq[String]]): Boolean =
    form.get("announceNewsletter")
      .flatMap(_.headOption)
      .exists(v => Set("on", "true").contains(v.trim.toLowerCase))

  case class SeasonAnnouncement(title: String, message: String)

  def buildSeasonAnnouncement(season: EditorialSeason): SeasonAnnouncement = {
    val trimmedName = season.name.trim
    val tagline = season.tagline.map(_.trim).filter(_.nonEmpty)
    val openingEssayLink = season.openingEssay.map(_.trim).filter(_.nonEmpty)

    val title = s"Nueva temporada activa: $trimmedName"
    val lines = Seq(
      Some(s"Comenzó «$trimmedName»."),
      tagline.map(t => s"Tagline: $t"),
      openingEssayLink.map(link => s"Opening essay: $link")
    ).flatten
    val message = lines.mkString("\n")

    SeasonAnnouncement(title, message)
  }

  def validate(data: Map[String, String], isCreate: Boolean): Either[Map[String, String], Parsed] = {
    val errors = scala.collection.mutable.Map.empty[String, String]

    val code = data.getOrElse("code", "").trim
    val name = data.getOrElse("name", "").trim
    val tagline = Option(data.getOrElse("tagline", "").trim).filter(_.nonEmpty)
    val openingEssay = Option(data.getOrElse("openingEssay", "").trim).filter(_.nonEmpty)
    val startsOn = parseDate(data.getOrElse("startsOn", ""), "startsOn", errors)
    val endsOn = parseDate(data.getOrElse("endsOn", ""), "endsOn", errors)

    if (isCreate) {
      if (code.isEmpty) errors += "code" -> "Código requerido."
      else if (!code.matches("^[a-z0-9-]+$")) errors += "code" -> "Código inválido (usa a-z, 0-9 y guiones)."
    }
    if (name.isEmpty) errors += "name" -> "Nombre requerido."
    for (start <- startsOn; end <- endsOn if !start.isBefore(end)) {
      errors += "endsOn" -> "La fecha de fin debe ser posterior a la de inicio."
    }

    if (errors.nonEmpty) Left(errors.toMap)
    else Right(Parsed(code, name, tagline, openingEssay, startsOn, endsOn))
  }

  private def parseDate(
    raw: String,
    key: String,
    errors: scala.collection.mutable.Map[String, String]
  ): Option[LocalDate] = {
    val value = raw.trim
    if (value.isEmpty) None
    else {
      scala.util.Try(LocalDate.parse(value)).toOption match {
        case Some(date) => Some(date)
        case None =>
          errors += key -> "Fecha inválida."
          None
      }
    }
  }
}

@Singleton
class AdminSeasonController @Inject()(
  cc: ControllerComponents,
  seasonRepo: EditorialSeasonRepository,
  newsletterRepo: NewsletterRepository,
  userRepo: UserRepository,
  notificationRepo: UserNotificationRepository,
  adminAction: AdminOnlyAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  import AdminSeasonValidation._

  def list(): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      seasonRepo.findAll().map { seasons =>
        Ok(views.html.admin.seasons.list(seasons))
      }
    }
  }

  def newForm(): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      Future.successful(Ok(views.html.admin.seasons.form(None, Map.empty, defaultFormData)))
    }
  }

  def create(): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      val formData = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty))
      validate(formData, isCreate = true) match {
        case Left(errors) =>
          Future.successful(BadRequest(views.html.admin.seasons.form(None, errors, formData)))
        case Right(parsed) =>
          seasonRepo.findByCode(parsed.code).flatMap {
            case Some(_) =>
              Future.successful(BadRequest(views.html.admin.seasons.form(None, Map("code" -> "Ya existe una temporada con ese código."), formData)))
            case None =>
              seasonRepo.create(parsed.code, parsed.name, parsed.tagline, parsed.openingEssay, parsed.startsOn, parsed.endsOn).map { id =>
                Redirect(routes.AdminSeasonController.editForm(id)).flashing("success" -> "Temporada creada.")
              }
          }
      }
    }
  }

  def editForm(id: Long): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      seasonRepo.findById(id).map {
        case Some(season) => Ok(views.html.admin.seasons.form(Some(season), Map.empty, toFormData(season)))
        case None         => NotFound("Temporada no encontrada")
      }
    }
  }

  def update(id: Long): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      seasonRepo.findById(id).flatMap {
        case None => Future.successful(NotFound("Temporada no encontrada"))
        case Some(existing) =>
          val formData = parseForm(request.body.asFormUrlEncoded.getOrElse(Map.empty)) + ("code" -> existing.code)
          validate(formData, isCreate = false) match {
            case Left(errors) =>
              Future.successful(BadRequest(views.html.admin.seasons.form(Some(existing), errors, formData)))
            case Right(parsed) =>
              seasonRepo.updateBasic(id, parsed.name, parsed.tagline, parsed.openingEssay, parsed.startsOn, parsed.endsOn).map { _ =>
                Redirect(routes.AdminSeasonController.list()).flashing("success" -> "Temporada actualizada.")
              }
          }
      }
    }
  }

  def setCurrent(id: Long): Action[AnyContent] = adminAction.async { implicit request =>
    CapabilityCheck.require(request, Cap.SeasonsManage) {
      val announceNewsletter = shouldAnnounceNewsletter(request.body.asFormUrlEncoded.getOrElse(Map.empty))

      seasonRepo.findById(id).flatMap {
        case None =>
          Future.successful(Redirect(routes.AdminSeasonController.list()).flashing("error" -> "No se encontró la temporada."))
        case Some(season) =>
          seasonRepo.setCurrent(id).flatMap {
            case false =>
              Future.successful(Redirect(routes.AdminSeasonController.list()).flashing("error" -> "No se encontró la temporada."))
            case true if !announceNewsletter =>
              Future.successful(Redirect(routes.AdminSeasonController.list()).flashing("success" -> "Temporada marcada como actual."))
            case true =>
              val announcement = buildSeasonAnnouncement(season)
              (for {
                emails <- newsletterRepo.findActiveEmails()
                recipients <- userRepo.findByEmails(emails)
                recipientIds = recipients.flatMap(_.id).distinct
                _ <- notificationRepo.createBroadcast(
                  userIds = recipientIds,
                  notificationType = "season_opening",
                  title = announcement.title,
                  message = announcement.message
                )
              } yield Redirect(routes.AdminSeasonController.list()).flashing("success" -> "Temporada marcada como actual y anuncio enviado."))
                .recover { case ex =>
                  logger.error(s"No se pudo enviar anuncio de apertura para temporada id=$id", ex)
                  Redirect(routes.AdminSeasonController.list())
                    .flashing("success" -> "Temporada marcada como actual, pero no se pudo enviar el anuncio de newsletter.")
                }
          }
      }
    }
  }

  private val defaultFormData: Map[String, String] = Map(
    "code" -> "",
    "name" -> "",
    "tagline" -> "",
    "openingEssay" -> "",
    "startsOn" -> "",
    "endsOn" -> ""
  )

  private def toFormData(season: EditorialSeason): Map[String, String] = Map(
    "code" -> season.code,
    "name" -> season.name,
    "tagline" -> season.tagline.orElse(season.description).getOrElse(""),
    "openingEssay" -> season.openingEssay.getOrElse(""),
    "startsOn" -> season.startsOn.map(_.toString).getOrElse(""),
    "endsOn" -> season.endsOn.map(_.toString).getOrElse("")
  )
}
