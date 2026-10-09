package co.caudal.application.auth;

import java.util.UUID;

/**
 * Who got a new password.
 *
 * @param userId the account
 * @param aqueductId the aqueduct of its first membership in force, null if it has none
 */
public record PasswordResetResult(UUID userId, UUID aqueductId) {}
