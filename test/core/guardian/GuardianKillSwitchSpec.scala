package core.guardian

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit, TestProbe}
import core._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import services.AgentSettingsLookup

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Tests for Issue #21 — V2 wiring: validates that the kill-switches
 * configured by the admin actually take effect inside the guardians.
 */
class GuardianKillSwitchSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers {

  implicit val ec: ExecutionContext = system.executionContext

  private def lookupWith(overrides: (String, String)*): AgentSettingsLookup =
    AgentSettingsLookup.fromMap(overrides.toMap)

  "DomainGuardian with engines.contact.enabled=false" should {
    "emit a DROP warning and not reply when ForwardContact arrives" in {
      val lookup   = lookupWith("engines.contact.enabled" -> "false")
      val guardian = spawn(DomainGuardian(null, null, null, null, null, lookup), "g-domain-killcontact")
      val probe    = TestProbe[ContactResponse]("p-contact")

      LoggingTestKit
        .warn("DROP contact")
        .withOccurrences(1)
        .expect {
          guardian ! ForwardContact(SubmitContact(Contact("Ana", "a@b.com", "hi"), probe.ref))
        }

      probe.expectNoMessage(300.millis)
    }
  }

  "CrossCutGuardian with engines.moderation.enabled=false" should {
    "fast-fail moderation requests with a pending_review verdict tagged kill_switch_off" in {
      val lookup   = lookupWith("engines.moderation.enabled" -> "false")
      val guardian = spawn(CrossCutGuardian(null, null, lookup), "g-crosscut-killmod")
      val probe    = TestProbe[ModerationResponse]("p-mod")

      guardian ! ForwardModeration(
        ModerateContent(
          contentId   = 42L,
          contentType = "publication",
          authorId    = 1L,
          title       = Some("t"),
          content     = "lorem",
          replyTo     = probe.ref
        )
      )

      val r = probe.expectMessageType[ModerationResult](2.seconds)
      r.contentId shouldBe 42L
      r.verdict   shouldBe "pending_review"
      r.flags     should contain ("kill_switch_off")
    }
  }

  "InfraGuardian with engines.pipeline.enabled=false" should {
    "fast-fail ProcessNewPublication with stage=guardian and correlationId=kill_switch" in {
      val lookup   = lookupWith("engines.pipeline.enabled" -> "false")
      val domain   = spawn(DomainGuardian(null, null, null, null, null), "g-domain-stub")
      val crossCut = spawn(CrossCutGuardian(null, null), "g-crosscut-stub")
      val infra    = spawn(InfraGuardian(domain, crossCut, lookup), "g-infra-killpipe")

      val healthProbe = TestProbe[GuardianHealth]("p-health")
      // Wait for wiring to complete (pipeline child spawned).
      eventually(timeout = 5.seconds) {
        infra ! GetInfraHealth(healthProbe.ref)
        val h = healthProbe.expectMessageType[GuardianHealth](2.seconds)
        h.children.keySet shouldBe Set("eventbus", "pipeline")
      }

      val probe = TestProbe[PipelineResponse]("p-pipe")
      infra ! ForwardPipeline(
        ProcessNewPublication(
          userId     = 1L,
          username   = "ana",
          userEmail  = None,
          title      = "t",
          content    = "b",
          excerpt    = None,
          coverImage = None,
          category   = "general",
          tags       = None,
          replyTo    = probe.ref
        )
      )

      val r = probe.expectMessageType[PipelineResponse](2.seconds)
      r match {
        case PipelineError(_, stage, corr) =>
          stage shouldBe "guardian"
          corr  shouldBe "kill_switch"
        case other => fail(s"expected PipelineError, got $other")
      }
    }
  }

  /** Mini retry helper for the wiring-async setup of InfraGuardian. */
  private def eventually[T](timeout: FiniteDuration)(body: => T): T = {
    val deadline = System.nanoTime() + timeout.toNanos
    var lastErr: Throwable = null
    while (System.nanoTime() < deadline) {
      try return body
      catch { case t: Throwable => lastErr = t; Thread.sleep(50) }
    }
    if (lastErr != null) throw lastErr else body
  }
}
