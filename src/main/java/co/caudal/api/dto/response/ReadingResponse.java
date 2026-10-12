package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A reading of the tank.
 *
 * @param id reading id
 * @param tankId tank
 * @param gaugeValue value read
 * @param effectiveGaugeValue value after corrections, only in lists
 * @param correctionCount number of corrections, only in lists
 * @param waterAppearanceCode appearance of the water
 * @param damageNoticed damage noticed
 * @param note note
 * @param observedAt observation moment
 * @param receivedAt arrival at the server
 * @param source origin
 * @param validationStatus ACCEPTED or FLAGGED
 * @param ruleSetId rules in force
 */
@Schema(description = "Lectura del tanque.")
public record ReadingResponse(
    @Schema(description = "Identificador.") UUID id,
    @Schema(description = "Tanque.") UUID tankId,
    @Schema(description = "Valor leído.", example = "2.10") BigDecimal gaugeValue,
    @Schema(description = "Valor efectivo (última corrección). Solo en listas.", nullable = true)
        BigDecimal effectiveGaugeValue,
    @Schema(description = "Número de correcciones. Solo en listas.", nullable = true)
        Integer correctionCount,
    @Schema(description = "Aspecto del agua.", example = "NORMAL") String waterAppearanceCode,
    @Schema(description = "Se notó daño.") boolean damageNoticed,
    @Schema(description = "Nota.", nullable = true) String note,
    @Schema(description = "Momento de la observación.") Instant observedAt,
    @Schema(description = "Llegada al servidor.") Instant receivedAt,
    @Schema(description = "Origen: MANUAL_APP, SENSOR o IMPORT.", example = "MANUAL_APP")
        String source,
    @Schema(description = "ACCEPTED o FLAGGED.", example = "ACCEPTED") String validationStatus,
    @Schema(description = "Reglas vigentes al validar.") UUID ruleSetId) {}
