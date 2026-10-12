package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line of the audit log. It has no request id and no HMAC of the address; the states are
 * redacted.
 *
 * @param id identifier of the entry
 * @param occurredAt when it happened
 * @param actorType USER, DEVICE, SYSTEM or API_KEY
 * @param actorId the actor, or null for the system
 * @param action what happened
 * @param entityType kind of entity
 * @param entityId id of the entity
 * @param beforeState state before the change, redacted, or null
 * @param afterState state after the change, redacted, or null
 * @param prevHash hash of the previous entry of the aqueduct
 * @param rowHash hash of this entry
 */
@Schema(description = "Entrada de la bitácora de auditoría. Estados redactados.")
public record AuditLogResponse(
    @Schema(example = "4211") long id,
    Instant occurredAt,
    @Schema(example = "USER") String actorType,
    @Schema(nullable = true) UUID actorId,
    @Schema(example = "UPDATE_USER") String action,
    @Schema(example = "user") String entityType,
    @Schema(example = "0190f3a2-7c1d-7b2e-9f11-3d4c5e6f7a81") String entityId,
    @Schema(nullable = true) Map<String, Object> beforeState,
    @Schema(nullable = true) Map<String, Object> afterState,
    String prevHash,
    String rowHash) {}
