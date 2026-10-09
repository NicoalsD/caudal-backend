package co.caudal.application.auth;

import java.util.UUID;

/**
 * Data of an account that the person may see about themself. It never carries the hash.
 *
 * @param id account id
 * @param username username
 * @param fullName full name (personal data: only returned to its owner)
 * @param status {@code ACTIVE}, {@code LOCKED} or {@code DISABLED}
 * @param mustChangePassword true until the first password change
 * @param privacyAcceptedVersion latest privacy notice version accepted, null if none
 */
public record UserProfile(
    UUID id,
    String username,
    String fullName,
    String status,
    boolean mustChangePassword,
    Integer privacyAcceptedVersion) {}
