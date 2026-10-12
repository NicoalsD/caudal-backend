package co.caudal.application.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A security event as the Board may read it (docs/API.md, section 18.2). The HMAC of the address
 * never leaves the database.
 *
 * @param id identifier of the event
 * @param type kind of event
 * @param severity how serious it is
 * @param actorUserId account involved, or null
 * @param deviceId device involved, or null
 * @param requestId correlation with the logs, or null
 * @param createdAt when it happened
 * @param details safe extra data, redacted
 */
public record SecurityEventView(
    long id,
    String type,
    String severity,
    UUID actorUserId,
    UUID deviceId,
    String requestId,
    Instant createdAt,
    Map<String, Object> details) {

  /**
   * Returns a copy of this event with other details.
   *
   * @param redacted the details, already redacted
   * @return the new event
   */
  public SecurityEventView withDetails(Map<String, Object> redacted) {
    return new SecurityEventView(
        id, type, severity, actorUserId, deviceId, requestId, createdAt, redacted);
  }
}
