package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A new retention period.
 *
 * @param retentionDays days the data is kept, from 1 to {@link FieldLimits#RETENTION_DAYS_MAX}
 */
@Schema(description = "Plazo de retención en días.")
public record UpdateRetentionPolicyRequest(
    @Schema(description = "Días que se conservan los datos.", example = "365")
        @NotNull
        @Min(1)
        @Max(FieldLimits.RETENTION_DAYS_MAX)
        Integer retentionDays) {}
