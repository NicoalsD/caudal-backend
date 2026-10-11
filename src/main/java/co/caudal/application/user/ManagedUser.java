package co.caudal.application.user;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as the Board sees it when it administers users (docs/API.md, section 3.1).
 *
 * @param id the account
 * @param username the username
 * @param fullName the full name (confidential)
 * @param status {@code ACTIVE}, {@code LOCKED} or {@code DISABLED}
 * @param lockedUntil end of a lock caused by failed logins, null otherwise
 * @param mustChangePassword true until the person sets a password
 * @param lastLoginAt last successful login, null if never
 */
public record ManagedUser(
    UUID id,
    String username,
    String fullName,
    String status,
    Instant lockedUntil,
    boolean mustChangePassword,
    Instant lastLoginAt) {}
