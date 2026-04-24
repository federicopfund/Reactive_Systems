package utils

import models.{AgentSettingCategory, AgentSettingType}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Verifica que el catálogo de settings (Issue #21):
 *  - Tiene exactamente las 28 variables documentadas.
 *  - Cada default es válido contra su propia regla de parseo.
 *  - `parseAndValidate` rechaza valores fuera de rango y acepta dentro.
 *  - Cubre todos los kill-switches por engine.
 *  - Cada categoría declarada tiene al menos una variable.
 */
class AgentSettingsCatalogSpec extends AnyWordSpec with Matchers {

  "AgentSettingsCatalog" should {

    "expose 32 variables" in {
      AgentSettingsCatalog.all.size shouldBe 32
      AgentSettingsCatalog.byKey.size shouldBe 32
    }

    "have unique keys" in {
      val keys = AgentSettingsCatalog.all.map(_.key)
      keys.distinct.size shouldBe keys.size
    }

    "every default value pass its own validation" in {
      AgentSettingsCatalog.all.foreach { d =>
        AgentSettingsCatalog.parseAndValidate(d, d.defaultValue) shouldBe a [Right[_, _]]
      }
    }

    "expose kill-switches for the 9 documented engines" in {
      val expected = Set("contact", "message", "publication", "gamification",
                         "notification", "moderation", "analytics",
                         "eventbus", "pipeline")
      val actual = AgentSettingsCatalog.all
        .filter(_.key.startsWith("engines."))
        .map(_.key.stripPrefix("engines.").stripSuffix(".enabled"))
        .toSet
      actual shouldBe expected
    }

    "every category in AgentSettingCategory.all has at least one setting" in {
      AgentSettingCategory.all.foreach { cat =>
        withClue(s"category=$cat: ") {
          AgentSettingsCatalog.all.exists(_.category == cat) shouldBe true
        }
      }
    }

    "parseAndValidate rejects values below min" in {
      val d = AgentSettingsCatalog.byKey("supervision.domain.maxRestarts")
      AgentSettingsCatalog.parseAndValidate(d, "0") shouldBe a [Left[_, _]]
    }

    "parseAndValidate rejects values above max" in {
      val d = AgentSettingsCatalog.byKey("supervision.domain.maxRestarts")
      AgentSettingsCatalog.parseAndValidate(d, "999999") shouldBe a [Left[_, _]]
    }

    "parseAndValidate accepts valid bool" in {
      val d = AgentSettingsCatalog.byKey("engines.contact.enabled")
      AgentSettingsCatalog.parseAndValidate(d, "false") shouldBe Right("false")
      AgentSettingsCatalog.parseAndValidate(d, "TRUE")  shouldBe Right("true")
    }

    "parseAndValidate rejects invalid bool" in {
      val d = AgentSettingsCatalog.byKey("engines.contact.enabled")
      AgentSettingsCatalog.parseAndValidate(d, "yes") shouldBe a [Left[_, _]]
    }

    "parseAndValidate normalizes integer strings" in {
      val d = AgentSettingsCatalog.byKey("dashboard.pollingSec")
      AgentSettingsCatalog.parseAndValidate(d, "  10  ") shouldBe Right("10")
    }

    "all duration settings are seconds and have IntT/LongT/DurationT type" in {
      AgentSettingsCatalog.all
        .filter(_.unit.contains("segundos"))
        .foreach { d =>
          d.valueType should (be (AgentSettingType.IntT)
            or be (AgentSettingType.LongT)
            or be (AgentSettingType.DurationT))
        }
    }
  }
}
