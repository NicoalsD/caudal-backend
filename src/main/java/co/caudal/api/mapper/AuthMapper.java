package co.caudal.api.mapper;

import co.caudal.api.dto.response.CurrentUserResponse;
import co.caudal.application.auth.CurrentUser;

/** Maps results of the authentication use cases to response DTOs. */
public final class AuthMapper {

  private AuthMapper() {}

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
