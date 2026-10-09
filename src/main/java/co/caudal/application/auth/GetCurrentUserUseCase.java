package co.caudal.application.auth;

import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PermissionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserProfilePort;
import co.caudal.domain.session.UnauthorizedException;
import java.time.Clock;
import java.util.UUID;

/**
 * Returns who the authenticated person is: profile, memberships in force and the permissions of the
 * role of the session. The permissions come from the database, so the answer reflects the current
 * matrix.
 */
public class GetCurrentUserUseCase {

  private final UserProfilePort profiles;
  private final MembershipPort memberships;
  private final PermissionPort permissions;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param profiles reads accounts
   * @param memberships reads memberships
   * @param permissions reads the permission matrix
   * @param unitOfWork transaction boundary with the session scope
   * @param clock source of time
   */
  public GetCurrentUserUseCase(
      UserProfilePort profiles,
      MembershipPort memberships,
      PermissionPort permissions,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.profiles = profiles;
    this.memberships = memberships;
    this.permissions = permissions;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Describes the current person.
   *
   * @param userId account of the session (from the validated token)
   * @param role role of the session (from the validated token)
   * @return the current user
   * @throws UnauthorizedException if the account no longer exists
   */
  public CurrentUser execute(UUID userId, String role) {
    return unitOfWork.executeAs(
        SessionScope.ofUser(userId),
        () -> {
          UserProfile profile =
              profiles.findProfile(userId).orElseThrow(UnauthorizedException::new);
          return new CurrentUser(
              profile,
              memberships.findActive(userId, clock.instant()).stream()
                  .map(
                      m ->
                          new MembershipSummary(
                              m.aqueductId(), m.role(), m.validFrom(), m.validTo()))
                  .toList(),
              permissions.findPermissionCodes(role).stream().sorted().toList());
        });
  }
}
