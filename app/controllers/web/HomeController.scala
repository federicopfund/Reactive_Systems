package controllers.web

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.Logging
import domains.contact.services.ReactiveContactAdapter
import shared.analytics.ReactiveAnalyticsAdapter
import domains.contact.repositories.ContactRepository
import domains.publications.repositories.{ReactionRepository, CommentRepository, PublicationCategoryRepository, EditorialArticleRepository}
import domains.gamification.repositories.BookmarkRepository
import domains.newsletter.repositories.NewsletterRepository
import domains.editorial.repositories.{ManifestoPillarRepository, LegalDocumentRepository, EditorialIdentityRepository, EditorialSeasonRepository}
import domains.collections.repositories.CollectionRepository
import domains.events.repositories.CommunityEventRepository
import domains.events.models.CommunityEvent
import domains.identity.repositories.UserRepository
import java.time.{Instant, ZoneId}
import domains.contact.engines.{Contact, ContactSubmitted, ContactError}
import _root_.controllers.actions.{OptionalAuthAction, OptionalAuthRequest}
import domains.editorial.models.EditorialSeason
import scala.concurrent.{ExecutionContext, Future}

// Form data case class (outside controller for Twirl template access)
case class ContactFormData(name: String, email: String, message: String)

@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents,
  adapter: ReactiveContactAdapter,
  analyticsAdapter: ReactiveAnalyticsAdapter,
  contactRepository: ContactRepository,
  publicationRepository: domains.publications.repositories.PublicationRepository,
  reactionRepo: ReactionRepository,
  commentRepo: CommentRepository,
  bookmarkRepo: BookmarkRepository,
  newsletterRepo: NewsletterRepository,
  categoryRepo: PublicationCategoryRepository,
  pillarRepo: ManifestoPillarRepository,
  legalRepo: LegalDocumentRepository,
  collectionRepo: CollectionRepository,
  editorialArticleRepo: EditorialArticleRepository,
  identityRepo: EditorialIdentityRepository,
  seasonRepo: EditorialSeasonRepository,
  eventRepo: CommunityEventRepository,
  userRepo: UserRepository,
  optionalAuth: OptionalAuthAction
)(implicit ec: ExecutionContext) extends BaseController with I18nSupport with Logging {

  // Form definition

  val contactForm: Form[ContactFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "message" -> nonEmptyText(minLength = 10)
    )(ContactFormData.apply)(ContactFormData.unapply)
  )

  private def loadCurrentSeason(): Future[Option[EditorialSeason]] =
    seasonRepo.findCurrent().recover {
      case ex: Throwable =>
        logger.warn("No se pudo cargar la temporada actual. Se aplicará fallback neutro en la UI pública.", ex)
        None
    }

  def index() = Action.async { implicit request: Request[AnyContent] =>
    analyticsAdapter.trackPageView("/", None, request.headers.get("Referer"))
    for {
      publications  <- publicationRepository.findAllApproved(limit = 6)
      pillars       <- pillarRepo.findActive()
      currentSeason <- loadCurrentSeason()
    } yield Ok(views.html.index(contactForm, publications, pillars, currentSeason))
  }

  def publicaciones() = Action.async { implicit request: Request[AnyContent] =>
    // Listado mezclado: publicaciones de usuarios + artículos editoriales del equipo
    for {
      dynamicPublications <- publicationRepository.findAllApproved(limit = 20)
      editorialArticles   <- editorialArticleRepo.findAllPublished(limit = 20)
      categories          <- categoryRepo.findActive()
      currentSeason       <- loadCurrentSeason()
    } yield Ok(views.html.publications.list(
      dynamicPublications, editorialArticles, categories, "", None, currentSeason
    ))
  }

  def temporadas() = Action.async { implicit request: Request[AnyContent] =>
    analyticsAdapter.trackPageView("/temporadas", None, request.headers.get("Referer"))
    seasonRepo.findAllChronologicalDesc().map { seasons =>
      Ok(views.html.seasons.list(seasons))
    }
  }

  def temporada(code: String) = Action.async { implicit request: Request[AnyContent] =>
    analyticsAdapter.trackPageView(s"/temporadas/$code", None, request.headers.get("Referer"))
    seasonRepo.findByCode(code).flatMap {
      case Some(season) =>
        val pubsFut = season.id match {
          case Some(seasonId) => publicationRepository.findApprovedBySeasonId(seasonId)
          case None           => Future.successful(Nil)
        }
        val eventsFut: Future[Seq[CommunityEvent]] = ((season.startsOn, season.endsOn) match {
          case (Some(s), Some(e)) =>
            val from = s.atStartOfDay(ZoneId.of("UTC")).toInstant
            val to   = e.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant
            eventRepo.findPublishedByRange(from, to)
          case _ if season.isCurrent =>
            eventRepo.findPublishedUpcoming(limit = 5)
          case (Some(s), None) =>
            val from = s.atStartOfDay(ZoneId.of("UTC")).toInstant
            eventRepo.findPublishedByRange(from, Instant.now())
          case _ =>
            Future.successful(Nil)
        }).recover { case _ => Nil }
        val collsFut      = collectionRepo.findPublishedWithCounts().recover { case _ => Nil }
        val allSeasonsFut = seasonRepo.findAllChronologicalDesc().recover { case _ => Nil }
        for {
          publications <- pubsFut
          events       <- eventsFut
          collections  <- collsFut
          allSeasons   <- allSeasonsFut
        } yield Ok(views.html.seasons.detail(season, publications, events, collections, allSeasons))
      case None =>
        Future.successful(NotFound(views.html.errors.notFound()))
    }
  }

  def publicacion(slug: String) = optionalAuth.async { implicit request: OptionalAuthRequest[AnyContent] =>
    // Primero buscar en publicaciones dinámicas
    publicationRepository.findBySlug(slug).flatMap {
      case Some(publication) if publication.status == "approved" =>
        // Track via AnalyticsEngine (fire-and-forget)
        val userId = request.userInfo.map(_._1)
        publication.id.foreach { pubId =>
          analyticsAdapter.trackPublicationView(pubId, userId)
        }
        analyticsAdapter.trackPageView(s"/publicacion/$slug", userId, request.headers.get("Referer"))
        // Incrementar contador de vistas
        publicationRepository.incrementViewCount(publication.id.get)
        val pubId = publication.id.get
        for {
          reactions <- reactionRepo.countByPublication(pubId)
          userReactions <- userId.map(uid => reactionRepo.getUserReactions(pubId, uid)).getOrElse(Future.successful(Set.empty[String]))
          comments <- commentRepo.findByPublicationId(pubId)
          isBookmarked <- userId.map(uid => bookmarkRepo.isBookmarked(uid, pubId)).getOrElse(Future.successful(false))
          authorUsername <- userRepo.findById(publication.userId).map(_.map(_.username).getOrElse("desconocido"))
        } yield {
          Ok(views.html.user.publicationPreview(
            publication,
            authorUsername,
            List.empty,
            reactions,
            userReactions,
            comments,
            isBookmarked,
            userId
          ))
        }
      case _ =>
        // Fallback: buscar en editorial_articles (piezas del equipo, DB-driven).
        editorialArticleRepo.findBySlug(slug).map {
          case Some(article) if article.isPublished =>
            // Track + view count (fire-and-forget)
            val userId = request.userInfo.map(_._1)
            analyticsAdapter.trackPageView(s"/publicacion/$slug", userId, request.headers.get("Referer"))
            article.id.foreach(editorialArticleRepo.incrementViewCount)
            Ok(views.html.publications.article(article))
          case _ =>
            NotFound("Publicación no encontrada")
        }
    }
  }

  def searchPublicaciones(q: String, category: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    val cleanCategory = category.map(_.trim).filter(_.nonEmpty)
    if (q.trim.isEmpty && cleanCategory.isEmpty) {
      Future.successful(Redirect(routes.HomeController.publicaciones()))
    } else {
      // El filtro `category` viene como nombre humano ("Tutorial"); buscamos
      // su slug para filtrar también editorial_articles.
      for {
        publications      <- publicationRepository.searchApproved(q, cleanCategory)
        categorySlug      <- cleanCategory match {
                               case Some(name) => categoryRepo.findByName(name).map(_.map(_.slug))
                               case None       => Future.successful(None)
                             }
        editorialArticles <- editorialArticleRepo.search(q, categorySlug)
        categories        <- categoryRepo.findActive()
        currentSeason     <- loadCurrentSeason()
      } yield Ok(views.html.publications.list(
        publications, editorialArticles, categories, q, cleanCategory, currentSeason
      ))
    }
  }

  def portafolio() = optionalAuth.async { implicit request: OptionalAuthRequest[AnyContent] =>
    val isAuthenticated = request.userInfo.isDefined
    val username        = request.userInfo.map(_._2)
    collectionRepo.findPublishedWithCounts().map { collections =>
      // Al volver al listado, limpiamos cualquier "breadcrumb" pendiente.
      Ok(views.html.collections.portfolio(isAuthenticated, username, collections))
        .removingFromSession("fromCollection", "fromCollectionName")
    }
  }

  def collectionDetail(slug: String) = optionalAuth.async { implicit request: OptionalAuthRequest[AnyContent] =>
    val isAuthenticated = request.userInfo.isDefined
    val username        = request.userInfo.map(_._2)
    collectionRepo.findBySlug(slug).flatMap {
      case Some(c) if c.isLive =>
        for {
          items <- collectionRepo.resolveItems(c.id.get)
          others <- collectionRepo.findPublishedWithCounts()
        } yield {
          val related = others.filter(_.collection.id != c.id).take(3)
          // Estamos parados en una colección: si había otro breadcrumb, lo borramos.
          Ok(views.html.collections.detail(isAuthenticated, username, c, items, related))
            .removingFromSession("fromCollection", "fromCollectionName")
        }
      case _ =>
        Future.successful(NotFound(views.html.errors.notFound()))
    }
  }

  /** Abre una pieza guardando en sesión la colección de origen para mostrar el botón "Volver". */
  def openFromCollection(colSlug: String, pieceSlug: String) = Action.async { implicit request: Request[AnyContent] =>
    collectionRepo.findBySlug(colSlug).map {
      case Some(c) if c.isLive =>
        Redirect(routes.HomeController.publicacion(pieceSlug))
          .addingToSession("fromCollection" -> c.slug, "fromCollectionName" -> c.name)
      case _ =>
        Redirect(routes.HomeController.publicacion(pieceSlug))
    }
  }

  def politicaPrivacidad() = Action.async { implicit request: Request[AnyContent] =>
    legalRepo.findPublishedBySlug("privacidad").map {
      case Some(doc) => Ok(views.html.legal.legalDocument(doc))
      case None      => NotFound("Documento no disponible")
    }
  }

  def terminosDeUso() = Action.async { implicit request: Request[AnyContent] =>
    legalRepo.findPublishedBySlug("terminos").map {
      case Some(doc) => Ok(views.html.legal.legalDocument(doc))
      case None      => NotFound("Documento no disponible")
    }
  }

  /** Issue #21 — Página /acerca-de: identidad editorial servida desde DB. */
  def acercaDe() = Action.async { implicit request: Request[AnyContent] =>
    identityRepo.findActive().map { blocks =>
      Ok(views.html.editorial.about(blocks))
    }
  }

  def submitContact() = Action.async { implicit request: Request[AnyContent] =>
    contactForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          publications  <- publicationRepository.findAllApproved(limit = 6)
          pillars       <- pillarRepo.findActive()
          currentSeason <- loadCurrentSeason()
        } yield BadRequest(views.html.index(formWithErrors, publications, pillars, currentSeason))
      },
      contactData => {
        val contact = Contact(contactData.name, contactData.email, contactData.message)
        adapter.submitContact(contact).map {
          case ContactSubmitted(id) =>
            Redirect(routes.HomeController.index()).flashing("success" -> "¡Mensaje recibido! Gracias por contactarnos, te responderemos lo antes posible.")
          case ContactError(msg) =>
            Redirect(routes.HomeController.index()).flashing("error" -> s"Error: $msg")
        }
      }
    )
  }

  // Newsletter subscription
  def subscribeNewsletter() = Action.async { implicit request: Request[AnyContent] =>
    request.body.asFormUrlEncoded.flatMap(_.get("email").flatMap(_.headOption)) match {
      case Some(email) if email.trim.nonEmpty =>
        val ip = request.remoteAddress
        newsletterRepo.subscribe(email, Some(ip)).map {
          case Right(_) =>
            Redirect(routes.HomeController.publicaciones()).flashing(
              "success" -> "¡Suscripción exitosa! Recibirás nuestras novedades."
            )
          case Left(msg) =>
            Redirect(routes.HomeController.publicaciones()).flashing(
              "info" -> msg
            )
        }
      case _ =>
        Future.successful(
          Redirect(routes.HomeController.publicaciones()).flashing(
            "error" -> "Por favor ingresa un email válido."
          )
        )
    }
  }
}
