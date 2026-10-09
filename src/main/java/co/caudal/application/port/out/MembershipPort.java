package co.caudal.application.port.out;

import co.caudal.domain.user.Membership;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads the memberships of an account. */
public interface MembershipPort {

  /**
   * Finds the memberships in force. Must run in a transaction opened with {@link
   * UnitOfWorkPort#executeAs}, because row level security shows an account only its own rows.
   *
   * @param userId the account
   * @param now the current instant
   * @return memberships in force, oldest first
   */
  List<Membership> findActive(UUID userId, Instant now);
}
