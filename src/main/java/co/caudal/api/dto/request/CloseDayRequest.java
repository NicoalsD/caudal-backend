package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * The closure of a day.
 *
 * @param notes the notes, up to 500 characters and 10 lines
 */
@Schema(description = "Cierre del día de servicio.")
public record CloseDayRequest(
    @Schema(
            description = "Notas, hasta 500 caracteres y 10 líneas.",
            example = "Día sin novedades mayores. Un turno a medias por la lluvia.",
            nullable = true)
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String notes) {}
