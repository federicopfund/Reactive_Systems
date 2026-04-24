package core

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

/**
 * Integration test for Issue #14 — EventBusEngine on Akka Cluster DistributedPubSub.
 *
 * Boots the same `eventbus-cluster` config used in production (1-node cluster on
 * 127.0.0.1:2552 to avoid colliding with a running app on 2551), subscribes a
 * probe to a topic, publishes a DomainEvent and asserts the probe receives it.
 */
class EventBusEngineClusterSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory.parseString(
        """
          |akka.actor.provider = "cluster"
          |akka.actor.serialization-bindings { "core.DomainEvent" = jackson-json }
          |akka.remote.artery.canonical.hostname = "127.0.0.1"
          |akka.remote.artery.canonical.port = 2552
          |akka.cluster.seed-nodes = ["akka://EventBusEngineClusterSpec@127.0.0.1:2552"]
          |akka.cluster.downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
          |akka.cluster.split-brain-resolver.active-strategy = keep-majority
          |akka.extensions = ["akka.cluster.pubsub.DistributedPubSub"]
          |""".stripMargin
      )
    )
    with AnyWordSpecLike
    with Matchers {
  "EventBusEngine (DistributedPubSub)" should {

    "deliver published events to subscribers of the matching topic" in {
      val bus   = spawn(EventBusEngine(), "bus-topic")
      val probe = TestProbe[DomainEvent]("subscriber-publication")

      bus ! SubscribeToEvents(probe.ref, Set("publication"))
      // El Mediator necesita un instante para registrar la suscripción cluster-wide.
      Thread.sleep(500)

      val evt = PublicationSubmittedEvent(
        publicationId = 42L,
        userId = 7L,
        username = "alice",
        title = "hola cluster",
        content = "x",
        category = "general",
        correlationId = "test-001"
      )
      bus ! PublishEvent(evt)

      val received = probe.expectMessageType[PublicationSubmittedEvent](3.seconds)
      received.publicationId shouldBe 42L
      received.correlationId shouldBe "test-001"
    }

    "deliver to wildcard \"*\" subscribers regardless of topic" in {
      val bus     = spawn(EventBusEngine(), "bus-wildcard")
      val starSub = TestProbe[DomainEvent]("subscriber-star")

      bus ! SubscribeToEvents(starSub.ref, Set("*"))
      Thread.sleep(500)

      bus ! PublishEvent(BadgeEarnedEvent(
        userId = 1L,
        badgeKey = "first-post",
        triggerType = "publication",
        correlationId = "test-002"
      ))

      val received = starSub.expectMessageType[BadgeEarnedEvent](3.seconds)
      received.badgeKey shouldBe "first-post"
    }

    "expose metrics with totalEventsPublished increasing per Publish" in {
      val bus     = spawn(EventBusEngine(), "bus-metrics")
      val metrics = TestProbe[EventBusResponse]("metrics-probe")

      bus ! PublishEvent(NotificationDeliveredEvent(
        userId = 9L, channel = "inapp", notificationType = "test",
        correlationId = "m-1"
      ))
      bus ! PublishEvent(NotificationDeliveredEvent(
        userId = 9L, channel = "inapp", notificationType = "test",
        correlationId = "m-2"
      ))

      bus ! GetEventBusMetrics(metrics.ref)
      val snap = metrics.expectMessageType[EventBusMetrics](3.seconds)
      snap.totalEventsPublished should be >= 2L
      snap.recentEvents should contain ("notification.delivered")
    }
  }
}
