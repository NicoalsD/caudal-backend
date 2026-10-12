package co.caudal.application.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * The filters of the audit log. Any of them may be null.
 *
 * @param from only entries that occurred at or after this moment
 * @param to only entries that occurred at or before this moment
 * @param action only this action, such as {@code RULESET_ACTIVATED}
 * @param entityType only this kind of entity
 * @param actorId only the entries of this account
 */
public record AuditLogFilter(
    Instant from, Instant to, String action, String entityType, UUID actorId) {}
