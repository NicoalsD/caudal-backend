package co.caudal.application.auth;

import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.user.Membership;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.util.UUID;

/**
 * Issues an access token for another aqueduct of the same person (docs/API.md, section 2.9). A
 * token carries one aqueduct, the active one, so a person with memberships in several aqueducts
 * switches between them without signing in again. The refresh token does not change.
 */
public class SwitchAqueductUseCase {

  private final MembershipPort memberships;
  private final TokenVersionPort tokenVersions;
  private final AccessTokenIssuerPort accessTokens;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param memberships memberships of the person
   * @param tokenVersions current token version of the account
   * @param accessTokens issues the new token
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public SwitchAqueductUseCase(
      MembershipPort memberships,
      TokenVersionPort tokenVersions,
      AccessTokenIssuerPort accessTokens,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.memberships = memberships;
    this.tokenVersions = tokenVersions;
    this.accessTokens = accessTokens;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Switches the active aqueduct.
   *
   * @param caller who asks
   * @param aqueductId the aqueduct to switch to
   * @return the new access token
   * @throws DomainException with {@code FORBIDDEN} if the person has no current membership there
   */
  public IssuedAccessToken execute(Caller caller, UUID aqueductId) {
    return unitOfWork.executeAs(
        SessionScope.ofUser(caller.userId()),
        () -> {
          Membership membership =
              memberships.findActive(caller.userId(), clock.instant()).stream()
                  .filter(m -> m.aqueductId().equals(aqueductId))
                  .findFirst()
                  .orElseThrow(() -> new DomainException(ErrorCode.FORBIDDEN));
          int version =
              tokenVersions
                  .findCurrentVersion(caller.userId())
                  .orElseThrow(SessionRevokedException::new);
          return accessTokens.issue(
              new AccessTokenSubject(
                  caller.userId(), membership.role(), membership.aqueductId(), version));
        });
  }
}
