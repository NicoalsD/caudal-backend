package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A manual command for a valve (docs/API.md, section 16.6). The reason is checked by the use case,
 * so that a missing or short one answers 422 REASON_REQUIRED.
 *
 * @param valveId the valve
 * @param command OPEN or CLOSE
 * @param manualReason why, 10 to 500 characters
 * @param notBefore the command must not run before this moment
 * @param expiresAt the command must not run from this moment on, after not_before
 */
@Schema(description = "Comando manual de una válvula motorizada, con motivo.")
public record CreateManualValveCommandRequest(
    @Schema(description = "Válvula motorizada del acueducto con actuador activo.") @NotNull
        UUID valveId,
    @Schema(description = "OPEN (abrir) o CLOSE (cerrar).", example = "CLOSE")
        @NotNull
        @Pattern(regexp = FieldLimits.VALVE_COMMAND_PATTERN)
        String command,
    @Schema(
            description = "Motivo, de 10 a 500 caracteres. Si falta responde 422 REASON_REQUIRED.",
            example = "Fuga visible en el tubo; cierre preventivo.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String manualReason,
    @Schema(description = "No ejecutar antes de este momento.") @NotNull Instant notBefore,
    @Schema(description = "No ejecutar desde este momento; debe ser posterior a not_before.")
        @NotNull
        Instant expiresAt) {}
