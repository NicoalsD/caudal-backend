package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A part of the network tree.
 *
 * @param kind SECTOR or VALVE
 * @param id the part
 * @param code its code
 * @param name its name
 * @param isActive whether it is in use
 * @param households households served by the part and everything under it
 * @param activeValves active valves of the part and everything under it
 * @param children the parts it holds
 */
@Schema(
    description =
        "Parte del árbol de la red: un sector con sus válvulas y subsectores, o una válvula.")
public record NetworkNodeResponse(
    @Schema(description = "SECTOR o VALVE.", example = "SECTOR") String kind,
    @Schema(description = "Identificador.") UUID id,
    @Schema(description = "Código.", example = "ALTO") String code,
    @Schema(description = "Nombre.", example = "El Alto") String name,
    @Schema(description = "Si está en uso.") boolean isActive,
    @Schema(description = "Viviendas de la parte y de todo lo que contiene (solo activas).")
        int households,
    @Schema(description = "Válvulas activas de la parte y de todo lo que contiene.")
        int activeValves,
    @Schema(description = "Partes que contiene.") List<NetworkNodeResponse> children) {}
