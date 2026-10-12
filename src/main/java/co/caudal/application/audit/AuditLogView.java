package co.caudal.application.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line of the audit log as the Board may read it (docs/API.md, section 18.1). The request id
 * and the HMAC of the address never leave the database.
 *
 * @param id identifier of the entry
 * @param occurredAt when it happened
 * @param actorType kind of actor: USER, DEVICE, SYSTEM or API_KEY
 * @param actorId the actor, null for the system
 * @param action what happened
 * @param entityType kind of entity
 * @param entityId id of the entity
 * @param beforeState state before the change, redacted, or null
 * @param afterState state after the change, redacted, or null
 * @param prevHash hash of the previous entry of the aqueduct
 * @param rowHash hash of this entry, chained to prevHash
 */
public record AuditLogView(
    long id,
    Instant occurredAt,
    String actorType,
    UUID actorId,
    String action,
    String entityType,
    String entityId,
    Map<String, Object> beforeState,
    Map<String, Object> afterState,
    String prevHash,
    String rowHash) {

  /**
   * Returns a copy of this entry with other states.
   *
   * @param before the state before, already redacted
   * @param after the state after, already redacted
   * @return the new entry
   */
  public AuditLogView withStates(Map<String, Object> before, Map<String, Object> after) {
    return new AuditLogView(
        id,
        occurredAt,
        actorType,
        actorId,
        action,
        entityType,
        entityId,
        before,
        after,
        prevHash,
        rowHash);
  }
}
