package domains.publications.engines.pipeline

/**
 * Protocolo de respuestas del PublicationPipelineEngine (Saga Orchestrator).
 *
 * Cada rama del saga produce exactamente una respuesta al caller:
 *   - [[PipelineSuccess]]         → pieza creada y efectos laterales despachados.
 *   - [[PipelineRejected]]        → rechazada por moderación automática.
 *   - [[PipelineError]]           → fallo irrecuperable en alguna etapa crítica.
 *   - [[PipelineMetricsSnapshot]] → snapshot de throughput/latencia (solo para [[GetPipelineMetrics]]).
 *
 * Las tres primeras son mutuamente excluyentes y exhaustivas (sealed trait),
 * garantizando que el caller recibe siempre exactamente una señal de terminación.
 */
sealed trait PipelineResponse

case class PipelineSuccess(
  publicationId:      Long,
  moderationVerdict:  String,
  moderationScore:    Double,
  processingTimeMs:   Long,
  correlationId:      String
) extends PipelineResponse

case class PipelineRejected(
  reason:            String,
  moderationScore:   Double,
  flags:             List[String],
  correlationId:     String
) extends PipelineResponse

case class PipelineError(
  reason:        String,
  stage:         String,
  correlationId: String
) extends PipelineResponse

case class PipelineMetricsSnapshot(
  totalProcessed:      Long,
  totalApproved:       Long,
  totalRejected:       Long,
  totalErrors:         Long,
  avgProcessingTimeMs: Long
) extends PipelineResponse
