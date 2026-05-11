package controllers.web

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{Lang, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.stubMessagesApi
import domains.editorial.models.EditorialSeason
import domains.publications.models.{Publication, PublicationWithAuthor}
import java.time.{Instant, LocalDate}

class TemporadasViewsSpec extends AnyWordSpec with Matchers {

  implicit val request = FakeRequest("GET", "/temporadas")
  implicit val messages = MessagesImpl(Lang("es"), stubMessagesApi())

  "views.html.temporadas" should {
    "render links to season detail pages using season code" in {
      val season = EditorialSeason(
        id = Some(1L),
        code = "2026-q2",
        name = "Temporada 2026 Q2",
        description = Some("Cadencia reactiva"),
        startsOn = Some(LocalDate.parse("2026-04-01")),
        endsOn = Some(LocalDate.parse("2026-06-30")),
        isCurrent = true,
        createdAt = Instant.now()
      )

      val html = views.html.seasons.list(Seq(season)).body
      html should include("/temporadas/2026-q2")
      html should include("Temporada 2026 Q2")
    }
  }

  "views.html.temporadaDetail" should {
    "render the index of associated publications" in {
      val season = EditorialSeason(
        id = Some(1L),
        code = "2026-q2",
        name = "Temporada 2026 Q2",
        createdAt = Instant.now()
      )
      val publication = PublicationWithAuthor(
        publication = Publication(
          id = Some(7L),
          userId = 10L,
          title = "Mensajería reactiva",
          slug = "mensajeria-reactiva",
          content = "contenido",
          category = "article",
          status = "approved",
          seasonId = Some(1L)
        ),
        authorUsername = "ana",
        authorFullName = "Ana Gómez"
      )

      val html = views.html.seasons.detail(season, List(publication)).body
      html should include("Índice de piezas asociadas (1)")
      html should include("/publicaciones/mensajeria-reactiva")
      html should include("Mensajería reactiva")
    }
  }
}
