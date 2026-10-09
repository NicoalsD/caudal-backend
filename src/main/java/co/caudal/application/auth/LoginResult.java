package co.caudal.application.auth;

import java.util.UUID;

/**
 * Result of a successful login.
 *
 * @param accessToken signed access token
 * @param refreshToken refresh token for the cookie
 * @param user the person and the session they got
 */
public record LoginResult(
    IssuedAccessToken accessToken, IssuedRefreshToken refreshToken, SessionUser user) {

  /**
   * The person and the session they got.
   *
   * @param id account id
   * @param username username
   * @param fullName full name
   * @param role role in the active aqueduct
   * @param aqueductId active aqueduct
   * @param mustChangePassword true until the first password change
   */
  public record SessionUser(
      UUID id,
      String username,
      String fullName,
      String role,
      UUID aqueductId,
      boolean mustChangePassword) {}
}
