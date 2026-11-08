package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * The new settings of the autopilot.
 *
 * @param mode MANUAL, ASSISTED or AUTOMATIC
 * @param operatorAbsenceMinutes minutes without human action before AUTOMATIC acts
 * @param reserveFloorPct reserve floor, in percent
 */
@Schema(description = "Modo y parámetros del piloto automático del acueducto.")
public record UpdateAutopilotSettingsRequest(
    @Schema(
            description =
                "MANUAL (solo muestra), ASSISTED (una persona acepta o rechaza) o AUTOMATIC"
                    + " (ejecuta si el operario no actuó).",
            example = "ASSISTED")
        @NotNull
        @Pattern(regexp = FieldLimits.AUTOPILOT_MODE_PATTERN)
        String mode,
    @Schema(
            description = "Minutos sin acción humana antes de que AUTOMATIC actúe: 1 a 1440.",
            example = "30")
        @NotNull
        @Min(FieldLimits.OPERATOR_ABSENCE_MINUTES_MIN)
        @Max(FieldLimits.OPERATOR_ABSENCE_MINUTES_MAX)
        Integer operatorAbsenceMinutes,
    @Schema(description = "Piso de reserva en % del tanque: 0 a 100.", example = "25")
        @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        BigDecimal reserveFloorPct) {}
