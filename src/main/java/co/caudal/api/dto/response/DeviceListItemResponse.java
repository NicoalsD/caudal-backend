package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A device in the list. It never carries public keys, fingerprints, nonces or secrets.
 *
 * @param id device id
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name name
 * @param tankId the tank of a level sensor
 * @param valveId the valve of a valve actuator
 * @param status ACTIVE, SUSPENDED or RETIRED
 * @param createdAt when it was registered
 * @param lastSeenAt last contact
 * @param activeKey the key valid now, or null
 */
@Schema(description = "Dispositivo de campo en la lista.")
public record DeviceListItemResponse(
    @Schema(description = "Identificador del dispositivo.") UUID id,
    @Schema(description = "Tipo de dispositivo.", example = "LEVEL_SENSOR") String kind,
    @Schema(description = "Nombre.", example = "Sensor del tanque principal") String name,
    @Schema(description = "Tanque que mide.", nullable = true) UUID tankId,
    @Schema(description = "Válvula que mueve.", nullable = true) UUID valveId,
    @Schema(description = "Estado: ACTIVE, SUSPENDED o RETIRED.", example = "ACTIVE") String status,
    @Schema(description = "Momento del registro.") Instant createdAt,
    @Schema(description = "Última conexión; nulo si nunca reportó.", nullable = true)
        Instant lastSeenAt,
    @Schema(description = "Clave vigente; nula si el dispositivo no tiene una.", nullable = true)
        DeviceKeyValidityResponse activeKey) {}
