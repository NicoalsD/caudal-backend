package co.caudal.application.command;

import java.util.Map;
import java.util.UUID;

/**
 * One line of the audit log.
 *
 * @param aqueductId aqueduct of the hash chain
 * @param actorId the account that acted
 * @param action what happened, for example {@code UPDATE_USER}
 * @param entityType kind of the entity
 * @param entityId id of the entity
 * @param ipHmac HMAC of the address of the caller, or null
 * @param afterState what the entity looks like after the change, or null
 */
public record AuditEntry(
    UUID aqueductId,
    UUID actorId,
    String action,
    String entityType,
    String entityId,
    String ipHmac,
    Map<String, Object> afterState) {

  /** Freezes the state. */
  public AuditEntry {
    afterState = afterState == null ? null : Map.copyOf(afterState);
  }
}
