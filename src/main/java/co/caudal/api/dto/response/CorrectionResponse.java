package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A new correction and the effective value it gives.
 *
 * @param id the correction
 * @param readingId the reading
 * @param correctedGaugeValue the right value
 * @param reason why
 * @param correctedBy who corrected it
 * @param createdAt when
 * @param effectiveGaugeValue the value that counts now
 */
@Schema(description = "Corrección creada y el valor efectivo nuevo de la lectura.")
public record CorrectionResponse(
    @Schema(description = "Identificador.") UUID id,
    @Schema(description = "Lectura.") UUID readingId,
    @Schema(description = "Valor corregido.") BigDecimal correctedGaugeValue,
    @Schema(description = "Motivo.") String reason,
    @Schema(description = "Quién la corrigió.") UUID correctedBy,
    @Schema(description = "Cuándo.") Instant createdAt,
    @Schema(description = "Valor efectivo de la lectura después de la corrección.")
        BigDecimal effectiveGaugeValue) {}
