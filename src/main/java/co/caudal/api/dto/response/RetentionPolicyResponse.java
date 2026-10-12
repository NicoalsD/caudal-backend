package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A retention period.
 *
 * @param dataClass the class of data
 * @param retentionDays days the data is kept
 * @param updatedBy the account that set it
 * @param updatedAt when it was set
 */
@Schema(description = "Plazo de retención de una clase de datos.")
public record RetentionPolicyResponse(
    @Schema(example = "SECURITY_EVENTS") String dataClass,
    @Schema(example = "365") int retentionDays,
    UUID updatedBy,
    Instant updatedAt) {}
