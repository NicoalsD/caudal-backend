package co.caudal.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A valve command with its status (docs/API.md, section 16.3).
 *
 * @param id the command
 * @param valveId the valve it moves
 * @param command OPEN or CLOSE
 * @param status the status after the confirmation
 * @param notBefore the command must not run before this moment
 * @param expiresAt the command must not run from this moment on
 */
public record ValveCommandResponse(
    UUID id, UUID valveId, String command, String status, Instant notBefore, Instant expiresAt) {}
