package core.guardian

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import repositories._
import services.{AgentSettingsLookup, EmailService}

import scala.concurrent.{ExecutionContext, Promise}

/**
 * RootGuardian — Behavior raíz del único `ActorSystem` (Issue #15).
 *
 * Spawnea las 3 capas en orden y resuelve un `Promise[Refs]` con las refs de
 * los guardians para que el `Module` (Guice) pueda exponerlas a los adapters.
 *
 * El sistema no recibe más mensajes después del setup — toda la mensajería
 * pasa por los guardians.
 */
object RootGuardian {

  final case class Refs(
    domain:   ActorRef[DomainGuardianCommand],
    crossCut: ActorRef[CrossCutGuardianCommand],
    infra:    ActorRef[InfraGuardianCommand]
  )

  def apply(
    contactRepo:      ContactRepository,
    messageRepo:      PrivateMessageRepository,
    notificationRepo: UserNotificationRepository,
    publicationRepo:  PublicationRepository,
    badgeRepo:        BadgeRepository,
    emailService:     EmailService,
    promise:          Promise[Refs],
    lookup:           AgentSettingsLookup = AgentSettingsLookup.Defaults
  )(implicit ec: ExecutionContext): Behavior[Nothing] =
    Behaviors.setup[Nothing] { ctx =>
      ctx.log.info("[Root] Reactive Manifesto — single ActorSystem booting 3 guardian layers")

      val domain   = ctx.spawn(DomainGuardian(contactRepo, messageRepo, notificationRepo, publicationRepo, badgeRepo, lookup), "domain")
      val crossCut = ctx.spawn(CrossCutGuardian(notificationRepo, emailService, lookup),                                       "cross-cut")
      val infra    = ctx.spawn(InfraGuardian(domain, crossCut, lookup),                                                        "infra")

      promise.success(Refs(domain, crossCut, infra))
      Behaviors.empty
    }
}
