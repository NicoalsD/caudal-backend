package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A question to the assistant.
 *
 * @param question the question, 3 to 500 characters
 */
@Schema(description = "Pregunta en lenguaje natural sobre el estado del acueducto.")
public record AskAssistantRequest(
    @Schema(
            description = "Pregunta de 3 a 500 caracteres.",
            example = "¿Alcanza el agua para mañana?")
        @NotNull
        @Size(min = FieldLimits.ASSISTANT_QUESTION_MIN, max = FieldLimits.ASSISTANT_QUESTION_MAX)
        String question) {}
