package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The result of an offline batch.
 *
 * @param syncBatchId the batch
 * @param itemsReceived readings it had
 * @param itemsAccepted readings stored or recognized as retries
 * @param itemsRejected readings refused
 * @param idempotentReplay true if the same batch had already been received
 * @param results one result per reading, in the order they were sent
 */
@Schema(description = "Resultado de un lote de la cola offline.")
public record ReadingBatchResponse(
    @Schema(description = "Lote.") UUID syncBatchId,
    @Schema(description = "Lecturas recibidas.") int itemsReceived,
    @Schema(description = "Lecturas guardadas o reconocidas como reintento.") int itemsAccepted,
    @Schema(description = "Lecturas rechazadas.") int itemsRejected,
    @Schema(description = "Verdadero si el mismo lote ya se había recibido.")
        boolean idempotentReplay,
    @Schema(description = "Un resultado por lectura, en el orden del envío.") List<Item> results) {

  /**
   * The result of one reading.
   *
   * @param id the reading
   * @param status ACCEPTED, FLAGGED, REPLAYED or REJECTED
   * @param error why it was refused, or null
   */
  @Schema(description = "Resultado de una lectura del lote.")
  public record Item(
      @Schema(description = "Lectura.") UUID id,
      @Schema(description = "ACCEPTED, FLAGGED, REPLAYED o REJECTED.", example = "ACCEPTED")
          String status,
      @Schema(description = "Motivo del rechazo; nulo si se guardó.", nullable = true)
          ItemError error) {}

  /**
   * Why a reading was refused.
   *
   * @param code the error code, for example GAUGE_OUT_OF_RANGE
   * @param details the values that explain it
   */
  @Schema(description = "Error de una lectura del lote.")
  public record ItemError(
      @Schema(description = "Código del error.", example = "GAUGE_OUT_OF_RANGE") String code,
      @Schema(description = "Detalles del error.") Map<String, Object> details) {}
}
