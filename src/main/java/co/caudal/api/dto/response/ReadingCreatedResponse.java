package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Result of recording a reading.
 *
 * @param reading the stored reading
 * @param issues the problems found
 * @param idempotentReplay true if the reading already existed
 */
@Schema(description = "Lectura registrada y los problemas que encontró la validación.")
public record ReadingCreatedResponse(
    @Schema(description = "Lectura guardada.") ReadingResponse reading,
    @Schema(description = "Marcas de la validación (DUPLICATE, SUDDEN_JUMP).") List<Issue> issues,
    @Schema(description = "Verdadero si la lectura ya existía con el mismo contenido.")
        boolean idempotentReplay) {

  /**
   * A problem found.
   *
   * @param code DUPLICATE or SUDDEN_JUMP
   * @param severity INFO or WARNING
   * @param details parameters
   */
  @Schema(description = "Marca de la validación de una lectura.")
  public record Issue(
      @Schema(description = "Código.", example = "SUDDEN_JUMP") String code,
      @Schema(description = "Severidad.", example = "WARNING") String severity,
      @Schema(description = "Parámetros de la marca.") Map<String, Object> details) {}
}
