package co.caudal.application.auth;

/**
 * Result of a refresh.
 *
 * @param accessToken new access token
 * @param refreshToken new refresh token for the cookie (the previous one is no longer valid)
 */
public record RefreshResult(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {}
