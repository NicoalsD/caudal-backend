package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A security event. It has no HMAC of the address and its details are already redacted.
 *
 * @param id identifier of the event
 * @param type kind of event
 * @param severity how serious it is
 * @param actorUserId account involved, or null
 * @param deviceId device involved, or null
 * @param requestId correlation with the logs, or null
 * @param createdAt when it happened
 * @param details safe extra data
 */
@Schema(description = "Evento de seguridad del acueducto. Sin HMAC de la IP ni secretos.")
public record SecurityEventResponse(
    @Schema(example = "4211") long id,
    @Schema(example = "PERMISSION_DENIED") String type,
    @Schema(example = "MEDIUM") String severity,
    @Schema(nullable = true) UUID actorUserId,
    @Schema(nullable = true) UUID deviceId,
    @Schema(nullable = true, example = "7f3c2a9e-1d4b-4e1a-9c0e-2b8f5a6d1e33") String requestId,
    Instant createdAt,
    Map<String, Object> details) {}
