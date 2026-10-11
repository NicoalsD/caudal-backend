package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The aqueduct to make active.
 *
 * @param aqueductId the aqueduct
 */
@Schema(description = "Acueducto que pasa a ser el activo de la sesión.")
public record SwitchAqueductRequest(
    @Schema(description = "Acueducto con una membresía vigente.") @NotNull UUID aqueductId) {}
