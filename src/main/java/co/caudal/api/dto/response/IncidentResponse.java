package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An incident. It has no data of the reporter: neither the hash of the tracking code nor the HMAC
 * of the address.
 *
 * @param id the incident
 * @param source PUBLIC_FORM, OPERATOR, ANOMALY or IMPORT
 * @param categoryCode the damage category
 * @param sectorId the sector, or null
 * @param description what happened
 * @param locationHint where, or null
 * @param status the status
 * @param reportedAt when it was reported
 */
@Schema(description = "Reporte de daño del acueducto. Sin datos del reportante.")
public record IncidentResponse(
    UUID id,
    @Schema(example = "OPERATOR") String source,
    @Schema(example = "LEAK") String categoryCode,
    @Schema(nullable = true) UUID sectorId,
    @Schema(example = "Gotea el tubo junto a la escuela.") String description,
    @Schema(nullable = true, example = "Frente a la escuela") String locationHint,
    @Schema(example = "REPORTED") String status,
    Instant reportedAt) {}
