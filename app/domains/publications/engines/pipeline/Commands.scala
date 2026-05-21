package domains.publications.engines.pipeline

import akka.actor.typed.ActorRef
import java.time.Instant
import shared.moderation.ModerationResponse

/**
 * Protocolo de comandos del PublicationPipelineEngine (Saga Orchestrator).
 *
 * Tres niveles de visibilidad:
 *   - Comandos públicos      → API externa (adapters, guardian).
 *   - Comandos internos      → `private[engines]`, resultados de pasos del saga
 *                              enviados al actor mediante `messageAdapter`.
 *   - SagaContext            → `private[engines]`, estado transportado entre
 *                              etapas del saga sin cruzar fronteras de módulo.
 */
sealed trait PipelineCommand

// ── API pública ────────────────────────────────────────────────────────────

case class ProcessNewPublication(
  userId:     Long,
  username:   String,
  userEmail:  Option[String],
  title:      String,
  content:    String,
  excerpt:    Option[String],
  coverImage: Option[String],
  category:   String,
  tags:       Option[String],
  replyTo:    ActorRef[PipelineResponse]
) extends PipelineCommand

case class GetPipelineMetrics(
  replyTo: ActorRef[PipelineResponse]
) extends PipelineCommand

// ── Protocolo interno — pasos del saga (solo visible dentro del package engines) ──

private[engines] case class ModerationStepCompleted(
  result:      ModerationResponse,
  sagaContext: SagaContext
) extends PipelineCommand

private[engines] case class PublicationStepCompleted(
  result:             domains.publications.engines.publication.PublicationResponse,
  moderationVerdict:  String,
  moderationScore:    Double,
  sagaContext:        SagaContext
) extends PipelineCommand

// ── Estado interno del saga ────────────────────────────────────────────────

private[engines] case class SagaContext(
  correlationId: String,
  userId:        Long,
  username:      String,
  userEmail:     Option[String],
  title:         String,
  content:       String,
  excerpt:       Option[String],
  coverImage:    Option[String],
  category:      String,
  tags:          Option[String],
  startedAt:     Instant,
  replyTo:       ActorRef[PipelineResponse]
)
