package domains.publications.engines

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domains.publications.engines.pipeline._
import domains.publications.engines.publication.{PublicationResponse, PublicationCreatedOk, PublicationError, CreatePublication}
import scala.concurrent.ExecutionContext
import java.time.Instant
import shared.moderation.{ModerationCommand, ModerationResponse, ModerateContent, ModerationResult}
import domains.messaging.engines.{NotificationCommand, SendNotification}
import domains.gamification.engines.{GamificationCommand, CheckBadges}
import shared.analytics.{AnalyticsCommand, TrackEvent}
import shared.eventbus.{EventBusCommand, PublishEvent}
import shared.{PublicationSubmittedEvent, ContentModeratedEvent, PipelineCompletedEvent}

/**
 * PublicationPipelineEngine — Saga Orchestrator para el ciclo de publicaciones.
 *
 * Orquesta el flujo completo de una publicación a través de múltiples agentes,
 * implementando el patrón Saga con compensación:
 *
 *   ╔══════════╗    ╔══════════════╗    ╔═══════════════╗    ╔═══════════════╗
 *   ║ Content  ║───▶║  Moderation  ║───▶║  Publication   ║───▶║ Notification  ║
 *   ║ Received ║    ║   Engine     ║    ║   Engine       ║    ║   Engine      ║
 *   ╚══════════╝    ╚══════════════╝    ╚═══════════════╝    ╚═══════════════╝
 *                                              │                     │
 *                         ╔══════════════╗     │    ╔═══════════════╗│
 *                         ║ Gamification ║◀────┘    ║  Analytics    ║◀┘
 *                         ║   Engine     ║          ║   Engine      ║
 *                         ╚══════════════╝          ╚═══════════════╝
 *
 * Stages:
 *   1. RECEIVE:   Content arrives → publish domain event → send to Moderation (Ask)
 *   2. MODERATE:  Moderation result → auto_rejected? → compensate. Otherwise continue.
 *   3. CREATE:    Create publication via PublicationEngine (Ask)
 *   4. SIDE EFFECTS: Notify author + Check badges + Track analytics (Tell, parallel)
 *   5. COMPLETE:  Publish pipeline.completed event → reply to caller
 *
 * Protocolo separado en:
 *   - [[pipeline.Commands]]   → comandos públicos + internos del saga + SagaContext
 *   - [[pipeline.Responses]]  → respuestas al caller
 *
 * Patrones implementados:
 *   - Saga (Orchestration): coordinación explícita de multi-step workflow
 *   - Message Adapter: conversión de respuestas tipadas entre agentes
 *   - Correlation ID: trazabilidad end-to-end
 *   - Compensating Action: notificación de rechazo al autor
 *   - Pipeline Metrics: tracking de throughput y latencia
 *
 * Principios Reactivos:
 *   - Message-Driven: cada stage es un mensaje, sin llamadas síncronas
 *   - Resilient: fallos en stages no-críticos no abortan el pipeline
 *   - Responsive: respuesta al caller apenas el stage crítico completa
 *   - Elastic: pipelines concurrentes, cada uno es un flujo de mensajes independiente
 */
object PublicationPipelineEngine {

  /** Estado de throughput/latencia, privado al objeto. */
  private case class Metrics(
    totalProcessed:       Long = 0,
    totalApproved:        Long = 0,
    totalRejected:        Long = 0,
    totalErrors:          Long = 0,
    totalProcessingTimeMs: Long = 0
  )

  def apply(
    moderationEngine:   ActorRef[ModerationCommand],
    publicationEngine:  ActorRef[publication.PublicationCommand],
    notificationEngine: ActorRef[NotificationCommand],
    gamificationEngine: ActorRef[GamificationCommand],
    analyticsEngine:    ActorRef[AnalyticsCommand],
    eventBus:           ActorRef[EventBusCommand]
  )(implicit ec: ExecutionContext): Behavior[PipelineCommand] =
    Behaviors.setup { context =>
      context.log.info("[Pipeline] Publication Pipeline Engine started — Saga Orchestrator ready")
      active(moderationEngine, publicationEngine, notificationEngine, gamificationEngine, analyticsEngine, eventBus, Metrics())
    }

  private def active(
    moderationEngine:   ActorRef[ModerationCommand],
    publicationEngine:  ActorRef[publication.PublicationCommand],
    notificationEngine: ActorRef[NotificationCommand],
    gamificationEngine: ActorRef[GamificationCommand],
    analyticsEngine:    ActorRef[AnalyticsCommand],
    eventBus:           ActorRef[EventBusCommand],
    metrics:            Metrics
  )(implicit ec: ExecutionContext): Behavior[PipelineCommand] =

    Behaviors.receive { (context, message) =>
      message match {

        // ═══════════════════════════════════════════════
        // STAGE 1: Receive content → Moderate (Ask)
        // ═══════════════════════════════════════════════
        case ProcessNewPublication(userId, username, userEmail, title, content, excerpt, coverImage, category, tags, replyTo) =>
          val correlationId = java.util.UUID.randomUUID().toString.take(8)
          context.log.info(s"[Pipeline][$correlationId] ▶ Stage 1/4: RECEIVE — '$title' by $username")

          val sagaCtx = SagaContext(
            correlationId = correlationId,
            userId        = userId,
            username      = username,
            userEmail     = userEmail,
            title         = title,
            content       = content,
            excerpt       = excerpt,
            coverImage    = coverImage,
            category      = category,
            tags          = tags,
            startedAt     = Instant.now(),
            replyTo       = replyTo
          )

          eventBus ! PublishEvent(PublicationSubmittedEvent(
            publicationId = 0L,
            userId        = userId,
            username      = username,
            title         = title,
            content       = content,
            category      = category,
            correlationId = correlationId
          ))

          analyticsEngine ! TrackEvent("pipeline.started", Some(userId), Map(
            "title"         -> title,
            "category"      -> category,
            "correlationId" -> correlationId
          ))

          val moderationAdapter = context.messageAdapter[ModerationResponse] { result =>
            ModerationStepCompleted(result, sagaCtx)
          }

          moderationEngine ! ModerateContent(
            contentId   = 0L,
            contentType = "publication",
            authorId    = userId,
            title       = Some(title),
            content     = content,
            replyTo     = moderationAdapter
          )
          Behaviors.same

        // ═══════════════════════════════════════════════
        // STAGE 2: Moderation result → Create or Reject
        // ═══════════════════════════════════════════════
        case ModerationStepCompleted(result, sagaCtx) =>
          result match {
            case ModerationResult(_, verdict, flags, score) =>
              context.log.info(s"[Pipeline][${sagaCtx.correlationId}] ▶ Stage 2/4: MODERATE — verdict=$verdict score=$score")

              eventBus ! PublishEvent(ContentModeratedEvent(
                contentId   = 0L,
                contentType = "publication",
                verdict     = verdict,
                score       = score,
                flags       = flags,
                correlationId = sagaCtx.correlationId
              ))

              if (verdict == "auto_rejected") {
                context.log.warn(s"[Pipeline][${sagaCtx.correlationId}] ✗ AUTO-REJECTED: ${flags.mkString(", ")}")

                notificationEngine ! SendNotification(
                  userId           = sagaCtx.userId,
                  userEmail        = sagaCtx.userEmail,
                  notificationType = "moderation_rejected",
                  title            = "Contenido rechazado automáticamente",
                  message          = s"Tu publicación '${sagaCtx.title}' no pasó la moderación: ${flags.mkString(", ")}",
                  publicationId    = None,
                  channels         = Set("inapp", "email"),
                  replyTo          = None
                )

                analyticsEngine ! TrackEvent("pipeline.rejected", Some(sagaCtx.userId), Map(
                  "verdict"       -> verdict,
                  "score"         -> score.toString,
                  "correlationId" -> sagaCtx.correlationId
                ))

                sagaCtx.replyTo ! PipelineRejected(
                  reason          = s"Moderación automática: $verdict",
                  moderationScore = score,
                  flags           = flags,
                  correlationId   = sagaCtx.correlationId
                )

                val elapsed    = java.time.Duration.between(sagaCtx.startedAt, Instant.now()).toMillis
                val newMetrics = metrics.copy(
                  totalProcessed        = metrics.totalProcessed + 1,
                  totalRejected         = metrics.totalRejected  + 1,
                  totalProcessingTimeMs = metrics.totalProcessingTimeMs + elapsed
                )
                active(moderationEngine, publicationEngine, notificationEngine, gamificationEngine, analyticsEngine, eventBus, newMetrics)

              } else {
                context.log.info(s"[Pipeline][${sagaCtx.correlationId}] ▶ Stage 3/4: CREATE — sending to PublicationEngine")

                val publicationAdapter = context.messageAdapter[PublicationResponse] { pubResult =>
                  PublicationStepCompleted(pubResult, verdict, score, sagaCtx)
                }

                publicationEngine ! CreatePublication(
                  userId     = sagaCtx.userId,
                  username   = sagaCtx.username,
                  title      = sagaCtx.title,
                  content    = sagaCtx.content,
                  excerpt    = sagaCtx.excerpt,
                  coverImage = sagaCtx.coverImage,
                  category   = sagaCtx.category,
                  tags       = sagaCtx.tags,
                  replyTo    = publicationAdapter
                )
                Behaviors.same
              }
          }

        // ═══════════════════════════════════════════════
        // STAGE 3-4: Publication created → Side effects
        // ═══════════════════════════════════════════════
        case PublicationStepCompleted(result, moderationVerdict, moderationScore, sagaCtx) =>
          val elapsed = java.time.Duration.between(sagaCtx.startedAt, Instant.now()).toMillis
          result match {
            case PublicationCreatedOk(publicationId) =>
              context.log.info(s"[Pipeline][${sagaCtx.correlationId}] ▶ Stage 4/4: SIDE EFFECTS — pub #$publicationId created in ${elapsed}ms")

              notificationEngine ! SendNotification(
                userId           = sagaCtx.userId,
                userEmail        = sagaCtx.userEmail,
                notificationType = "publication_created",
                title            = "Publicación recibida",
                message          = s"Tu publicación '${sagaCtx.title}' fue recibida y está $moderationVerdict",
                publicationId    = Some(publicationId),
                channels         = Set("inapp"),
                replyTo          = None
              )

              gamificationEngine ! CheckBadges(
                userId      = sagaCtx.userId,
                triggerType = "publication",
                metadata    = Map("publicationCount" -> 1L),
                replyTo     = None
              )

              analyticsEngine ! TrackEvent("pipeline.completed", Some(sagaCtx.userId), Map(
                "publicationId"   -> publicationId.toString,
                "verdict"         -> moderationVerdict,
                "score"           -> moderationScore.toString,
                "processingTimeMs"-> elapsed.toString,
                "correlationId"   -> sagaCtx.correlationId
              ))

              eventBus ! PublishEvent(PipelineCompletedEvent(
                publicationId    = publicationId,
                userId           = sagaCtx.userId,
                verdict          = moderationVerdict,
                processingTimeMs = elapsed,
                correlationId    = sagaCtx.correlationId
              ))

              sagaCtx.replyTo ! PipelineSuccess(
                publicationId     = publicationId,
                moderationVerdict = moderationVerdict,
                moderationScore   = moderationScore,
                processingTimeMs  = elapsed,
                correlationId     = sagaCtx.correlationId
              )

              val newMetrics = metrics.copy(
                totalProcessed        = metrics.totalProcessed + 1,
                totalApproved         = metrics.totalApproved  + 1,
                totalProcessingTimeMs = metrics.totalProcessingTimeMs + elapsed
              )
              active(moderationEngine, publicationEngine, notificationEngine, gamificationEngine, analyticsEngine, eventBus, newMetrics)

            case PublicationError(reason) =>
              context.log.error(s"[Pipeline][${sagaCtx.correlationId}] ✗ CREATE FAILED: $reason")

              analyticsEngine ! TrackEvent("pipeline.error", Some(sagaCtx.userId), Map(
                "stage"         -> "create",
                "reason"        -> reason,
                "correlationId" -> sagaCtx.correlationId
              ))

              sagaCtx.replyTo ! PipelineError(
                reason        = reason,
                stage         = "create",
                correlationId = sagaCtx.correlationId
              )

              val newMetrics = metrics.copy(
                totalProcessed        = metrics.totalProcessed + 1,
                totalErrors           = metrics.totalErrors    + 1,
                totalProcessingTimeMs = metrics.totalProcessingTimeMs + elapsed
              )
              active(moderationEngine, publicationEngine, notificationEngine, gamificationEngine, analyticsEngine, eventBus, newMetrics)

            case _ =>
              context.log.warn(s"[Pipeline][${sagaCtx.correlationId}] Unexpected publication response: $result")
              sagaCtx.replyTo ! PipelineError(
                reason        = s"Unexpected response: $result",
                stage         = "create",
                correlationId = sagaCtx.correlationId
              )
              Behaviors.same
          }

        // ═══════════════════════════════════════════════
        // METRICS: throughput y latencia del pipeline
        // ═══════════════════════════════════════════════
        case GetPipelineMetrics(replyTo) =>
          val avgTime = if (metrics.totalProcessed > 0)
            metrics.totalProcessingTimeMs / metrics.totalProcessed
          else 0L

          replyTo ! PipelineMetricsSnapshot(
            totalProcessed      = metrics.totalProcessed,
            totalApproved       = metrics.totalApproved,
            totalRejected       = metrics.totalRejected,
            totalErrors         = metrics.totalErrors,
            avgProcessingTimeMs = avgTime
          )
          Behaviors.same
      }
    }
}
