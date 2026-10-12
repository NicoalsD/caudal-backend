package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The result of an import of simulated history.
 *
 * @param importBatchId the import
 * @param rowsReceived rows it had
 * @param rowsAccepted rows stored
 * @param rowsRejected rows refused
 * @param rejections why each refused row was refused
 */
@Schema(description = "Resultado de una importación de historial simulado.")
public record ImportResponse(
    @Schema(description = "Importación.") UUID importBatchId,
    @Schema(description = "Filas recibidas.") int rowsReceived,
    @Schema(description = "Filas guardadas.") int rowsAccepted,
    @Schema(description = "Filas rechazadas.") int rowsRejected,
    @Schema(description = "Filas rechazadas y su motivo.") List<Rejection> rejections) {

  /**
   * A refused row.
   *
   * @param row its position in the file, from 1
   * @param code why
   */
  @Schema(description = "Fila rechazada.")
  public record Rejection(
      @Schema(description = "Posición en el archivo, desde 1.") int row,
      @Schema(description = "Código del error.", example = "GAUGE_OUT_OF_RANGE") String code) {}
}
