package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import models.EditorialSeason
import java.time.Instant

class AdminSeasonValidationSpec extends AnyWordSpec with Matchers {

  "AdminSeasonValidation.validate" should {
    "aceptar fechas válidas con inicio menor a fin" in {
      val data = Map(
        "code" -> "2026-q3",
        "name" -> "Temporada 2026 Q3",
        "tagline" -> "Tagline",
        "openingEssay" -> "Ensayo",
        "startsOn" -> "2026-07-01",
        "endsOn" -> "2026-09-30"
      )

      AdminSeasonValidation.validate(data, isCreate = true).isRight shouldBe true
    }

    "rechazar cuando startsOn no es menor que endsOn" in {
      val data = Map(
        "code" -> "2026-q3",
        "name" -> "Temporada 2026 Q3",
        "startsOn" -> "2026-09-30",
        "endsOn" -> "2026-09-30"
      )

      val result = AdminSeasonValidation.validate(data, isCreate = true)
      result.isLeft shouldBe true
      result.left.toOption.get should contain key "endsOn"
    }

    "rechazar código inválido al crear" in {
      val data = Map(
        "code" -> "Temporada 2026",
        "name" -> "Temporada 2026 Q3",
        "startsOn" -> "",
        "endsOn" -> ""
      )

      val result = AdminSeasonValidation.validate(data, isCreate = true)
      result.isLeft shouldBe true
      result.left.toOption.get should contain key "code"
    }
  }

  "AdminSeasonValidation.shouldAnnounceNewsletter" should {
    "return true only when checkbox value is present" in {
      AdminSeasonValidation.shouldAnnounceNewsletter(Map("announceNewsletter" -> Seq("on"))) shouldBe true
      AdminSeasonValidation.shouldAnnounceNewsletter(Map("announceNewsletter" -> Seq("true"))) shouldBe true
      AdminSeasonValidation.shouldAnnounceNewsletter(Map("announceNewsletter" -> Seq("1"))) shouldBe false
      AdminSeasonValidation.shouldAnnounceNewsletter(Map.empty) shouldBe false
    }
  }

  "AdminSeasonValidation.buildSeasonAnnouncement" should {
    "include season name, tagline and opening essay link when available" in {
      val season = EditorialSeason(
        id = Some(1L),
        code = "2026-q4",
        name = "Temporada 2026 Q4",
        tagline = Some("Nuevos ritmos editoriales"),
        openingEssay = Some("https://example.org/opening"),
        createdAt = Instant.parse("2026-10-01T00:00:00Z")
      )

      val announcement = AdminSeasonValidation.buildSeasonAnnouncement(season)
      announcement.title should include("Temporada 2026 Q4")
      announcement.message should include("Tagline: Nuevos ritmos editoriales")
      announcement.message should include("Opening essay: https://example.org/opening")
    }

    "remain valid when opening essay is absent" in {
      val season = EditorialSeason(
        id = Some(2L),
        code = "2027-q1",
        name = "Temporada 2027 Q1",
        tagline = Some("Foco en sistemas distribuidos"),
        openingEssay = None,
        createdAt = Instant.parse("2027-01-01T00:00:00Z")
      )

      val announcement = AdminSeasonValidation.buildSeasonAnnouncement(season)
      announcement.title should include("Temporada 2027 Q1")
      announcement.message should include("Tagline: Foco en sistemas distribuidos")
      announcement.message should not include ("Opening essay:")
    }
  }
}
