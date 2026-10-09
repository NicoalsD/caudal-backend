package co.caudal.application.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * What an access token says about a session: who, with which role, in which aqueduct and with which
 * version of the account (a change of password, role or status raises the version and invalidates
 * the token).
 *
 * @param userId the account
 * @param role code of the role in the active aqueduct
 * @param aqueductId the active aqueduct
 * @param tokenVersion {@code iam.users.token_version} when the token is issued
 */
public record AccessTokenSubject(UUID userId, String role, UUID aqueductId, int tokenVersion) {

  /** Validates that nothing is missing. */
  public AccessTokenSubject {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(aqueductId, "aqueductId");
  }
}
