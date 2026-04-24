package core.guardian

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Tests for Issue #15 — DomainGuardian boot, health snapshot and ref query.
 *
 * Engines are spawned with `null` repositories: they only get exercised when
 * a forward message arrives, and these tests do not exercise that path.
 */
class DomainGuardianSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers {

  implicit val ec: ExecutionContext = system.executionContext

  "DomainGuardian" should {

    "report all 4 domain children as Healthy at boot" in {
      val guardian = spawn(DomainGuardian(null, null, null, null, null), "guardian-boot")
      val probe    = TestProbe[GuardianHealth]("h-probe")

      guardian ! GetDomainHealth(probe.ref)

      val h = probe.expectMessageType[GuardianHealth](3.seconds)
      h.layer           shouldBe "domain"
      h.healthy         shouldBe true
      h.children.keySet shouldBe Set("contact", "message", "publication", "gamification")
      h.children.values.foreach(_.status shouldBe ChildStatus.Healthy)
    }

    "expose the 4 child ActorRefs via GetDomainRefs" in {
      val guardian = spawn(DomainGuardian(null, null, null, null, null), "guardian-refs")
      val probe    = TestProbe[DomainRefs]("refs-probe")

      guardian ! GetDomainRefs(probe.ref)

      val refs = probe.expectMessageType[DomainRefs](3.seconds)
      refs.contact      should not be null
      refs.message      should not be null
      refs.publication  should not be null
      refs.gamification should not be null
    }
  }
}
