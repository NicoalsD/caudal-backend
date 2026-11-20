package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The answer of the assistant.
 *
 * @param source LLM or RULES
 * @param model the model or the version of the rules
 * @param answer the answer in Spanish
 * @param generatedAt when it was produced
 */
@Schema(description = "Respuesta del asistente.")
public record AssistantAnswerResponse(
    @Schema(description = "LLM si respondió el modelo; RULES si respondieron las reglas.")
        String source,
    @Schema(description = "Modelo o versión de las reglas.") String model,
    @Schema(description = "Respuesta en español.") String answer,
    Instant generatedAt) {}
