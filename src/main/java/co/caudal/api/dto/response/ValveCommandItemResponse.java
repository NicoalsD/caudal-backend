package co.caudal.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A valve command handed to its actuator (docs/API.md, section 16.2).
 *
 * @param id the command
 * @param valveId the valve it moves
 * @param command OPEN or CLOSE
 * @param notBefore the command must not run before this moment
 * @param expiresAt the command must not run from this moment on
 */
public record ValveCommandItemResponse(
    UUID id, UUID valveId, String command, Instant notBefore, Instant expiresAt) {}
