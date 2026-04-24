package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import services.ReactiveContactAdapter
import services.ReactiveAnalyticsAdapter
import repositories.{
  ContactRepository, ReactionRepository, CommentRepository, BookmarkRepository,
  NewsletterRepository, PublicationCategoryRepository, ManifestoPillarRepository,
  LegalDocumentRepository, CollectionRepository, EditorialArticleRepository
}
import core.{Contact, ContactSubmitted, ContactError}
import actions.{OptionalAuthAction, OptionalAuthRequest}
import scala.concurrent.{ExecutionContext, Future}

// Form data case class (outside controller for Twirl template access)
case class ContactFormData(name: String, email: String, message: String)

@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents,
  adapter: ReactiveContactAdapter,
  analyticsAdapter: ReactiveAnalyticsAdapter,
  contactRepository: ContactRepository,
  publicationRepository: repositories.PublicationRepository,
  reactionRepo: ReactionRepository,
  commentRepo: CommentRepository,
  bookmarkRepo: BookmarkRepository,
  newsletterRepo: NewsletterRepository,
  categoryRepo: PublicationCategoryRepository,
  pillarRepo: ManifestoPillarRepository,
  legalRepo: LegalDocumentRepository,
  collectionRepo: CollectionRepository,
  editorialArticleRepo: EditorialArticleRepository,
  optionalAuth: OptionalAuthAction
)(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  // Form definition

  val contactForm: Form[ContactFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "message" -> nonEmptyText(minLength = 10)
    )(ContactFormData.apply)(ContactFormData.unapply)
  )

  def index() = Action.async { implicit request: Request[AnyContent] =>
    analyticsAdapter.trackPageView("/", None, request.headers.get("Referer"))
    for {
      publications <- publicationRepository.findAllApproved(limit = 6)
      pillars      <- pillarRepo.findActive()
    } yield Ok(views.html.index(contactForm, publications, pillars))
  }

  def publicaciones() = Action.async { implicit request: Request[AnyContent] =>
    // Listado mezclado: publicaciones de usuarios + artículos editoriales del equipo
    for {
      dynamicPublications <- publicationRepository.findAllApproved(limit = 20)
      editorialArticles   <- editorialArticleRepo.findAllPublished(limit = 20)
      categories          <- categoryRepo.findActive()
    } yield Ok(views.html.publicaciones(
      dynamicPublications, editorialArticles, categories, "", None
    ))
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
        } yield {
          Ok(views.html.user.publicationPreview(
            publication, 
            request.userInfo.map(_._2).getOrElse("Invitado"), 
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
            Ok(views.html.editorialArticleView(article))
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
      } yield Ok(views.html.publicaciones(
        publications, editorialArticles, categories, q, cleanCategory
      ))
    }
  }

  def portafolio() = optionalAuth.async { implicit request: OptionalAuthRequest[AnyContent] =>
    val isAuthenticated = request.userInfo.isDefined
    val username        = request.userInfo.map(_._2)
    collectionRepo.findPublishedWithCounts().map { collections =>
      // Al volver al listado, limpiamos cualquier "breadcrumb" pendiente.
      Ok(views.html.portafolio(isAuthenticated, username, collections))
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
          Ok(views.html.collectionDetail(isAuthenticated, username, c, items, related))
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

  def submitContact() = Action.async { implicit request: Request[AnyContent] =>
    contactForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          publications <- publicationRepository.findAllApproved(limit = 6)
          pillars      <- pillarRepo.findActive()
        } yield BadRequest(views.html.index(formWithErrors, publications, pillars))
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
