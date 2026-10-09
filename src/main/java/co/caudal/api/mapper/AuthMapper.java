package co.caudal.api.mapper;

import co.caudal.api.dto.response.CurrentUserResponse;
import co.caudal.api.dto.response.LoginResponse;
import co.caudal.application.auth.CurrentUser;
import co.caudal.application.auth.LoginResult;

/** Maps results of the authentication use cases to response DTOs. */
public final class AuthMapper {

  /** Value of {@code token_type}. */
  public static final String TOKEN_TYPE = "Bearer";

  private AuthMapper() {}

  /**
   * Maps a successful login.
   *
   * @param result result of the use case
   * @return the response, without the refresh token
   */
  public static LoginResponse toResponse(LoginResult result) {
    LoginResult.SessionUser user = result.user();
    return new LoginResponse(
        result.accessToken().value(),
        TOKEN_TYPE,
        result.accessToken().timeToLive().toSeconds(),
        new LoginResponse.SessionUserResponse(
            user.id(),
            user.username(),
            user.fullName(),
            user.role(),
            user.aqueductId(),
            user.mustChangePassword()));
  }

  /**
   * Maps the current user.
   *
   * @param user result of the use case
   * @return the response
   */
  public static CurrentUserResponse toResponse(CurrentUser user) {
    return new CurrentUserResponse(
        user.profile().id(),
        user.profile().username(),
        user.profile().fullName(),
        user.profile().status(),
        user.profile().mustChangePassword(),
        user.profile().privacyAcceptedVersion(),
        user.memberships().stream()
            .map(
                m ->
                    new CurrentUserResponse.MembershipResponse(
                        m.aqueductId(), m.role(), m.validFrom(), m.validTo()))
            .toList(),
        user.permissions());
  }
}
