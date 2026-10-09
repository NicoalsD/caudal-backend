package co.caudal.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Role of an account in one aqueduct during a period.
 *
 * @param aqueductId the aqueduct
 * @param role role code such as {@code OPERATOR}
 * @param validFrom start of the validity
 * @param validTo end of the validity, null while open
 */
public record Membership(UUID aqueductId, String role, Instant validFrom, Instant validTo) {

  /** Validates the required parts. */
  public Membership {
    Objects.requireNonNull(aqueductId, "aqueductId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(validFrom, "validFrom");
  }

  /**
   * Tells whether the membership is in force.
   *
   * @param now the current instant
   * @return true if it started and has not ended
   */
  public boolean isActiveAt(Instant now) {
    return !validFrom.isAfter(now) && (validTo == null || validTo.isAfter(now));
  }
}
