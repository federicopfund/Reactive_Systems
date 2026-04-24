import akka.actor.typed.ActorSystem
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.ConfigFactory
import core.guardian._
import repositories._
import services._

import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration._

/**
 * Module — Guice bindings (Issue #15).
 *
 * Reemplaza los 9 ActorSystems independientes por un único `ActorSystem`
 * llamado `reactive-manifesto`, cuya raíz (`RootGuardian`) spawnea las 3
 * capas de guardians y devuelve sus ActorRefs vía un `Promise` que se
 * resuelve durante el setup.
 *
 * Los adapters reciben directamente la `ActorRef[*GuardianCommand]` que
 * necesitan (no el `ActorSystem`), eliminando el acoplamiento de
 * infraestructura en sus constructores.
 *
 * El config de Akka Cluster (Issue #14) se aplica al ActorSystem completo
 * a través del bloque `eventbus-cluster` en application.conf — necesario
 * porque `EventBusEngine` ahora vive como hijo de `infra` dentro de este
 * mismo sistema.
 */
class Module extends AbstractModule {

  // ── ActorSystem único + 3 ActorRefs de guardians ──
  @Provides
  @Singleton
  def provideRefs(
    contactRepo:      ContactRepository,
    messageRepo:      PrivateMessageRepository,
    notificationRepo: UserNotificationRepository,
    publicationRepo:  PublicationRepository,
    badgeRepo:        BadgeRepository,
    emailService:     EmailService,
    agentSettings:    AgentSettingsService
  )(implicit ec: ExecutionContext): RootGuardian.Refs = {
    val rootConfig    = ConfigFactory.load()
    val clusterConfig = rootConfig.getConfig("eventbus-cluster").withFallback(rootConfig)

    val promise = Promise[RootGuardian.Refs]()
    val system = ActorSystem[Nothing](
      RootGuardian(contactRepo, messageRepo, notificationRepo, publicationRepo, badgeRepo, emailService, promise, agentSettings),
      "reactive-manifesto",
      clusterConfig
    )
    // Anclamos el ActorSystem al ciclo de vida (lo bindeamos abajo).
    _system = system
    Await.result(promise.future, 10.seconds)
  }

  // El sistema se mantiene vivo mientras el `Module` viva. Lo exponemos
  // por si algún componente necesita el `Scheduler` o el `dispatcher`.
  @volatile private var _system: ActorSystem[Nothing] = _

  @Provides
  @Singleton
  def provideActorSystem(refs: RootGuardian.Refs): ActorSystem[Nothing] = _system

  // Issue #15/#16: el HealthController usa AskPattern y necesita un
  // `akka.actor.typed.Scheduler` implícito. Lo derivamos del sistema raíz.
  @Provides @Singleton
  def provideTypedScheduler(system: ActorSystem[Nothing]): akka.actor.typed.Scheduler =
    system.scheduler

  @Provides @Singleton
  def domainRef(refs: RootGuardian.Refs): akka.actor.typed.ActorRef[DomainGuardianCommand]   = refs.domain
  @Provides @Singleton
  def crossCutRef(refs: RootGuardian.Refs): akka.actor.typed.ActorRef[CrossCutGuardianCommand] = refs.crossCut
  @Provides @Singleton
  def infraRef(refs: RootGuardian.Refs): akka.actor.typed.ActorRef[InfraGuardianCommand]     = refs.infra

  // ── Adapters (ahora reciben ActorRef, no ActorSystem) ─────────────────
  @Provides @Singleton
  def provideContactAdapter(d: akka.actor.typed.ActorRef[DomainGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveContactAdapter =
    new ReactiveContactAdapter(d, system.scheduler)

  @Provides @Singleton
  def provideMessageAdapter(d: akka.actor.typed.ActorRef[DomainGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveMessageAdapter =
    new ReactiveMessageAdapter(d, system.scheduler)

  @Provides @Singleton
  def providePublicationAdapter(d: akka.actor.typed.ActorRef[DomainGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactivePublicationAdapter =
    new ReactivePublicationAdapter(d, system.scheduler)

  @Provides @Singleton
  def provideGamificationAdapter(d: akka.actor.typed.ActorRef[DomainGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveGamificationAdapter =
    new ReactiveGamificationAdapter(d, system.scheduler)

  @Provides @Singleton
  def provideNotificationAdapter(cc: akka.actor.typed.ActorRef[CrossCutGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveNotificationAdapter =
    new ReactiveNotificationAdapter(cc, system.scheduler)

  @Provides @Singleton
  def provideModerationAdapter(cc: akka.actor.typed.ActorRef[CrossCutGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveModerationAdapter =
    new ReactiveModerationAdapter(cc, system.scheduler)

  @Provides @Singleton
  def provideAnalyticsAdapter(cc: akka.actor.typed.ActorRef[CrossCutGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveAnalyticsAdapter =
    new ReactiveAnalyticsAdapter(cc, system.scheduler)

  @Provides @Singleton
  def provideEventBusAdapter(infra: akka.actor.typed.ActorRef[InfraGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactiveEventBusAdapter =
    new ReactiveEventBusAdapter(infra, system.scheduler)

  @Provides @Singleton
  def providePipelineAdapter(infra: akka.actor.typed.ActorRef[InfraGuardianCommand], system: ActorSystem[Nothing])(implicit ec: ExecutionContext): ReactivePipelineAdapter =
    new ReactivePipelineAdapter(infra, system.scheduler)
}
