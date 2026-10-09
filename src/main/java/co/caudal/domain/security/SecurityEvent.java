package co.caudal.domain.security;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something worth monitoring that happened to a session or an account.
 *
 * <p>Details are already redacted: they never hold a password, a token, a code or an IP address
 * (only its HMAC).
 *
 * @param type kind of event
 * @param severity how serious it is
 * @param actorUserId account involved, null if none
 * @param aqueductId aqueduct involved, null for a global event
 * @param ipHmac keyed hash of the IP address, null if unknown
 * @param details safe extra data
 */
public record SecurityEvent(
    SecurityEventType type,
    Severity severity,
    UUID actorUserId,
    UUID aqueductId,
    String ipHmac,
    Map<String, Object> details) {

  /** Validates the required parts and freezes the details. */
  public SecurityEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(severity, "severity");
    details = Map.copyOf(Objects.requireNonNull(details, "details"));
  }
}
