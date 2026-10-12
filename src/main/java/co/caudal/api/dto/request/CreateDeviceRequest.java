package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A new field device.
 *
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name name
 * @param tankId the tank of a level sensor
 * @param valveId the valve of a valve actuator
 * @param firmwareVersion firmware version
 */
@Schema(description = "Dispositivo de campo nuevo: sensor de nivel, actuador de válvula o gateway.")
public record CreateDeviceRequest(
    @Schema(description = "LEVEL_SENSOR, VALVE_ACTUATOR o GATEWAY.", example = "LEVEL_SENSOR")
        @NotNull
        @Pattern(regexp = FieldLimits.DEVICE_KIND_PATTERN)
        String kind,
    @Schema(description = "Nombre: 2 a 60 caracteres.", example = "Sensor del tanque principal")
        @NotNull
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(
            description = "Tanque que mide. Obligatorio para LEVEL_SENSOR; no se envía en otros.",
            nullable = true)
        UUID tankId,
    @Schema(
            description =
                "Válvula que mueve. Obligatoria para VALVE_ACTUATOR; no se envía en otros.",
            nullable = true)
        UUID valveId,
    @Schema(description = "Versión del firmware: hasta 30 caracteres.", nullable = true)
        @Size(max = FieldLimits.FIRMWARE_VERSION_MAX)
        @Pattern(regexp = FieldLimits.SINGLE_LINE_PATTERN)
        String firmwareVersion) {}
