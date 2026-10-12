package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Change of a device status.
 *
 * @param status ACTIVE, SUSPENDED or RETIRED
 */
@Schema(description = "Cambio de estado de un dispositivo.")
public record UpdateDeviceRequest(
    @Schema(description = "ACTIVE, SUSPENDED o RETIRED.", example = "SUSPENDED")
        @NotNull
        @Pattern(regexp = FieldLimits.DEVICE_STATUS_PATTERN)
        String status) {}
