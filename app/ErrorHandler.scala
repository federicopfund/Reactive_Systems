import javax.inject._
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logging
import scala.concurrent._

@Singleton
class ErrorHandler @Inject() extends HttpErrorHandler with Logging {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(
      Status(statusCode)(views.html.errors.notFound()(request))
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"[500] ${request.method} ${request.uri}", exception)
    Future.successful(
      InternalServerError(views.html.errors.serverError()(request))
    )
  }
}
