package co.caudal.api.mapper;

import co.caudal.api.dto.response.CreatedUserResponse;
import co.caudal.api.dto.response.ManagedUserResponse;
import co.caudal.api.dto.response.MembershipResponse;
import co.caudal.application.user.CreatedUser;
import co.caudal.application.user.ManagedUser;
import co.caudal.application.user.MembershipView;

/** Maps results of the user administration use cases to response DTOs. */
public final class UserMapper {

  private UserMapper() {}

  /**
   * Maps a new account.
   *
   * @param user result of the command
   * @return the response, with the one-time code
   */
  public static CreatedUserResponse toResponse(CreatedUser user) {
    return new CreatedUserResponse(
        user.id(),
        user.username(),
        user.fullName(),
        user.status(),
        user.mustChangePassword(),
        new CreatedUserResponse.MembershipSummary(
            user.membershipId(), user.aqueductId(), user.role()),
        user.activationCode(),
        user.activationExpiresAt());
  }

  /**
   * Maps an administered account.
   *
   * @param user the account
   * @return the response
   */
  public static ManagedUserResponse toResponse(ManagedUser user) {
    return new ManagedUserResponse(
        user.id(),
        user.username(),
        user.fullName(),
        user.status(),
        user.lockedUntil(),
        user.mustChangePassword(),
        user.lastLoginAt());
  }

  /**
   * Maps a membership.
   *
   * @param membership the membership
   * @return the response
   */
  public static MembershipResponse toResponse(MembershipView membership) {
    return new MembershipResponse(
        membership.id(),
        membership.userId(),
        membership.aqueductId(),
        membership.roleCode(),
        membership.validFrom(),
        membership.validTo(),
        membership.grantedBy());
  }
}
