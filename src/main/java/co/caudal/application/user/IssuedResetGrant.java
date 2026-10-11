package co.caudal.application.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time code to set a new password, shown once.
 *
 * @param id the grant
 * @param userId the account
 * @param code the code, for example {@code ABCD-EFGH-JKMN}
 * @param expiresAt when it stops working
 */
public record IssuedResetGrant(UUID id, UUID userId, String code, Instant expiresAt) {

  @Override
  public String toString() {
    return "IssuedResetGrant[id=" + id + ", code=REDACTED]";
  }
}
