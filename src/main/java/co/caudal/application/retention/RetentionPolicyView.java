package co.caudal.application.retention;

import java.time.Instant;
import java.util.UUID;

/**
 * A retention period as the Board may read it (docs/API.md, section 18.3).
 *
 * @param dataClass the class of data, such as {@code SECURITY_EVENTS}
 * @param retentionDays days the data is kept
 * @param updatedBy the account that set the period
 * @param updatedAt when it was set
 */
public record RetentionPolicyView(
    String dataClass, int retentionDays, UUID updatedBy, Instant updatedAt) {}
