import akka.actor.typed.ActorSystem
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.ConfigFactory
import infrastructure.guardian._
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
// domain repositories
import domains.contact.repositories.ContactRepository
import domains.messaging.repositories.{PrivateMessageRepository, UserNotificationRepository}
import domains.publications.repositories.PublicationRepository
import domains.gamification.repositories.BadgeRepository
// domain + shared services
import domains.identity.services.EmailService
import domains.admin.services.AgentSettingsService
import domains.publications.services.{ReactivePublicationAdapter, ReactivePipelineAdapter}
import domains.gamification.services.ReactiveGamificationAdapter
import domains.messaging.services.{ReactiveMessageAdapter, ReactiveNotificationAdapter}
import domains.contact.services.ReactiveContactAdapter
import shared.analytics.ReactiveAnalyticsAdapter
import shared.moderation.ReactiveModerationAdapter
import shared.eventbus.ReactiveEventBusAdapter

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
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

  override def configure(): Unit = {
    // Inicialización eager: el ActorSystem se construye al arrancar la app
    // (no en el primer request). Evita "Shutdown in progress" cuando llegan
    // requests durante el hot-reload de dev mode.
    bind(classOf[ReactiveSystemHolder]).asEagerSingleton()
  }

  // ── ActorSystem único + 3 ActorRefs de guardians ──
  @Provides
  @Singleton
  def provideRefs(holder: ReactiveSystemHolder): RootGuardian.Refs = holder.refs

  @Provides
  @Singleton
  def provideActorSystem(holder: ReactiveSystemHolder): ActorSystem[Nothing] = holder.system

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

/**
 * Holder eager singleton: construye el ActorSystem raíz en el arranque
 * de la aplicación y registra su terminación con el ApplicationLifecycle
 * de Play. Esto evita el error "Shutdown in progress" que ocurría cuando
 * el ActorSystem se inicializaba perezosamente durante el primer request.
 */
@Singleton
class ReactiveSystemHolder @Inject() (
  contactRepo:      ContactRepository,
  messageRepo:      PrivateMessageRepository,
  notificationRepo: UserNotificationRepository,
  publicationRepo:  PublicationRepository,
  badgeRepo:        BadgeRepository,
  emailService:     EmailService,
  agentSettings:    AgentSettingsService,
  lifecycle:        ApplicationLifecycle
)(implicit ec: ExecutionContext) {

  private val rootConfig    = ConfigFactory.load()
  private val clusterConfig = rootConfig.getConfig("eventbus-cluster").withFallback(rootConfig)

  private val promise = Promise[RootGuardian.Refs]()

  val system: ActorSystem[Nothing] = ActorSystem[Nothing](
    RootGuardian(contactRepo, messageRepo, notificationRepo, publicationRepo, badgeRepo, emailService, promise, agentSettings),
    "reactive-manifesto",
    clusterConfig
  )

  val refs: RootGuardian.Refs = Await.result(promise.future, 10.seconds)

  lifecycle.addStopHook { () =>
    system.terminate()
    Future.successful(())
  }
}
