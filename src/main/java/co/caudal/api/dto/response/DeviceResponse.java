package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A field device.
 *
 * @param id device id
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name name
 * @param tankId the tank of a level sensor
 * @param valveId the valve of a valve actuator
 * @param status ACTIVE, SUSPENDED or RETIRED
 * @param firmwareVersion firmware version
 * @param lastSeenAt last contact
 */
@Schema(description = "Dispositivo de campo.")
public record DeviceResponse(
    @Schema(description = "Identificador del dispositivo.") UUID id,
    @Schema(description = "Tipo de dispositivo.", example = "LEVEL_SENSOR") String kind,
    @Schema(description = "Nombre.", example = "Sensor del tanque principal") String name,
    @Schema(description = "Tanque que mide.", nullable = true) UUID tankId,
    @Schema(description = "Válvula que mueve.", nullable = true) UUID valveId,
    @Schema(description = "Estado: ACTIVE, SUSPENDED o RETIRED.", example = "ACTIVE") String status,
    @Schema(description = "Versión del firmware.", nullable = true) String firmwareVersion,
    @Schema(description = "Última conexión; vacío si nunca reportó.", nullable = true)
        Instant lastSeenAt) {}
