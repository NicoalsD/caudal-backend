package co.caudal.application.auth;

import java.util.UUID;

/**
 * Result of a rotation: the account the session belongs to and the token that replaces the one
 * presented.
 *
 * @param userId owner of the session
 * @param next the new token for the cookie
 */
public record RotatedRefreshToken(UUID userId, IssuedRefreshToken next) {}
