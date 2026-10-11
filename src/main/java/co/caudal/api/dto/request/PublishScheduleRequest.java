package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The publication of an approved schedule.
 *
 * @param lockVersion the version the member saw
 */
@Schema(description = "Publicación de un horario aprobado.")
public record PublishScheduleRequest(
    @Schema(description = "Versión que vio el miembro de la Junta.", example = "1") @NotNull @Min(0)
        Integer lockVersion) {}
