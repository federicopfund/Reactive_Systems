package services

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

/**
 * Verifica que `AgentSettingsLookup` (Issue #21):
 *  - El lookup `Defaults` devuelve los defaults declarados en el catálogo.
 *  - El lookup `fromMap` devuelve el override cuando existe y cae al default
 *    cuando no existe.
 *  - `getInt`, `getBool`, `getDuration` parsean correctamente.
 */
class AgentSettingsLookupSpec extends AnyWordSpec with Matchers {

  "AgentSettingsLookup.Defaults" should {

    "return the catalog default for supervision.domain.maxRestarts" in {
      AgentSettingsLookup.Defaults.getInt("supervision.domain.maxRestarts") shouldBe 5
    }

    "return true for engines.contact.enabled (kill-switches default ON)" in {
      AgentSettingsLookup.Defaults.getBool("engines.contact.enabled") shouldBe true
      AgentSettingsLookup.Defaults.getBool("engines.pipeline.enabled") shouldBe true
    }

    "return 30s for heartbeat.domain.intervalSec" in {
      AgentSettingsLookup.Defaults.getDuration("heartbeat.domain.intervalSec") shouldBe 30.seconds
    }

    "return 1s for backoff.domain.minSec and 30s for max" in {
      AgentSettingsLookup.Defaults.getDuration("backoff.domain.minSec") shouldBe 1.second
      AgentSettingsLookup.Defaults.getDuration("backoff.domain.maxSec") shouldBe 30.seconds
    }
  }

  "AgentSettingsLookup.fromMap" should {

    "use override when present" in {
      val l = AgentSettingsLookup.fromMap(Map(
        "supervision.domain.maxRestarts" -> "12",
        "engines.contact.enabled"        -> "false",
        "heartbeat.infra.intervalSec"    -> "60"
      ))
      l.getInt("supervision.domain.maxRestarts") shouldBe 12
      l.getBool("engines.contact.enabled")       shouldBe false
      l.getDuration("heartbeat.infra.intervalSec") shouldBe 60.seconds
    }

    "fall back to catalog default when key not in map" in {
      val l = AgentSettingsLookup.fromMap(Map.empty)
      l.getInt("cb.moderation.failureThreshold") shouldBe 3
      l.getDuration("cb.moderation.resetSec")    shouldBe 30.seconds
    }
  }
}
