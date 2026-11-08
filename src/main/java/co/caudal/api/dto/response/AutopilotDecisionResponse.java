package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A decision of the autopilot.
 *
 * @param id the decision
 * @param createdAt when the cycle created it
 * @param source LLM or RULES
 * @param model the model or the version of the rules
 * @param action OPEN, CLOSE, WAIT or INSPECT
 * @param valveId the valve, or null
 * @param reason why, in Spanish
 * @param confidence from 0 to 1
 * @param status PENDING, ACCEPTED, REJECTED, EXECUTED, EXPIRED or SKIPPED
 * @param statusDetail why it was skipped or rejected, or null
 * @param decidedBy the person who resolved it, or null
 * @param decidedAt when it was resolved, or null
 * @param valveCommandId the command it created, or null
 */
@Schema(description = "Decisión del piloto automático.")
public record AutopilotDecisionResponse(
    UUID id,
    Instant createdAt,
    @Schema(description = "Quién la produjo: LLM o RULES (reglas deterministas).") String source,
    @Schema(description = "Modelo o versión de las reglas.") String model,
    @Schema(description = "OPEN, CLOSE, WAIT o INSPECT.") String action,
    @Schema(description = "Válvula de la acción; nula en WAIT e INSPECT.", nullable = true)
        UUID valveId,
    @Schema(description = "Motivo en español.") String reason,
    @Schema(description = "Confianza de 0 a 1.") BigDecimal confidence,
    @Schema(description = "PENDING, ACCEPTED, REJECTED, EXECUTED, EXPIRED o SKIPPED.")
        String status,
    @Schema(
            description =
                "Por qué se omitió (RESERVE_FLOOR, COMMAND_REFUSED) o el motivo del rechazo.",
            nullable = true)
        String statusDetail,
    @Schema(description = "Persona que la aceptó o rechazó.", nullable = true) UUID decidedBy,
    @Schema(description = "Momento en que se resolvió.", nullable = true) Instant decidedAt,
    @Schema(description = "Comando de llave que creó.", nullable = true) UUID valveCommandId) {}
