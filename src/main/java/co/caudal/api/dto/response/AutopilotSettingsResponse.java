package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The settings of the autopilot.
 *
 * @param mode MANUAL, ASSISTED or AUTOMATIC
 * @param operatorAbsenceMinutes minutes without human action before AUTOMATIC acts
 * @param reserveFloorPct reserve floor, in percent
 * @param updatedAt when they last changed
 */
@Schema(description = "Modo y parámetros del piloto automático.")
public record AutopilotSettingsResponse(
    @Schema(description = "MANUAL, ASSISTED o AUTOMATIC.") String mode,
    @Schema(description = "Minutos sin acción humana antes de que AUTOMATIC actúe.")
        int operatorAbsenceMinutes,
    @Schema(description = "Piso de reserva en % del tanque.") BigDecimal reserveFloorPct,
    @Schema(description = "Última actualización.") Instant updatedAt) {}
