package co.caudal.application.audit;

import java.time.Instant;

/**
 * The filters of the security events. Any of them may be null.
 *
 * @param type only this kind of event, such as {@code PERMISSION_DENIED}
 * @param severity only this severity
 * @param from only events created at or after this moment
 * @param to only events created at or before this moment
 */
public record SecurityEventFilter(String type, String severity, Instant from, Instant to) {}
