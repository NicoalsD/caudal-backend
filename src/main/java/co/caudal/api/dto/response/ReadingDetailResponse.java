package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One reading with its warnings and corrections.
 *
 * @param reading the reading, with its effective value
 * @param issues its warnings
 * @param corrections its corrections, oldest first
 */
@Schema(description = "Lectura con sus advertencias y su historial de correcciones.")
public record ReadingDetailResponse(
    @Schema(description = "La lectura, con su valor efectivo.") ReadingResponse reading,
    @Schema(description = "Advertencias de la validación.") List<Issue> issues,
    @Schema(description = "Correcciones, de la más antigua a la más reciente.")
        List<Correction> corrections) {

  /**
   * A warning.
   *
   * @param code DUPLICATE or SUDDEN_JUMP
   * @param severity INFO or WARNING
   * @param details the values that explain it
   */
  @Schema(description = "Advertencia de una lectura.")
  public record Issue(String code, String severity, Map<String, Object> details) {}

  /**
   * A correction.
   *
   * @param id the correction
   * @param correctedGaugeValue the right value
   * @param reason why
   * @param correctedBy who corrected it
   * @param createdAt when
   */
  @Schema(description = "Corrección de una lectura; el dato original no cambia.")
  public record Correction(
      @Schema(description = "Identificador.") UUID id,
      @Schema(description = "Valor corregido.", example = "2.00") BigDecimal correctedGaugeValue,
      @Schema(description = "Motivo.") String reason,
      @Schema(description = "Quién la corrigió.") UUID correctedBy,
      @Schema(description = "Cuándo.") Instant createdAt) {}
}
