package co.caudal.domain.autopilot;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.EnumSet;
import java.util.Set;

/**
 * Where a decision of the autopilot is in its life. A decision is born {@code PENDING} (or {@code
 * SKIPPED} when a guard refuses it) and leaves it once: accepted, rejected, executed or expired.
 * Every other move is refused with {@code INVALID_STATE_TRANSITION}.
 *
 * @pattern P18 State
 */
public enum DecisionStatus {
  /** Waiting for a person or for the next cycle. */
  PENDING,
  /** A person accepted it and the command was created. */
  ACCEPTED,
  /** A person rejected it. */
  REJECTED,
  /** The command was created by the autopilot, or the device confirmed an accepted one. */
  EXECUTED,
  /** A newer cycle replaced it before anyone resolved it. */
  EXPIRED,
  /** A guard refused it. */
  SKIPPED;

  /**
   * Restores the status from its code.
   *
   * @param code the stored code
   * @return the status
   * @throws IllegalArgumentException if the code is unknown
   */
  public static DecisionStatus of(String code) {
    return valueOf(code);
  }

  /**
   * Whether the decision can still be resolved by a person or replaced by a newer cycle.
   *
   * @return true only for {@code PENDING}
   */
  public boolean isOpen() {
    return this == PENDING;
  }

  private Set<DecisionStatus> allowed() {
    return switch (this) {
      case PENDING -> EnumSet.of(ACCEPTED, REJECTED, EXECUTED, EXPIRED, SKIPPED);
      case ACCEPTED -> EnumSet.of(EXECUTED);
      case REJECTED, EXECUTED, EXPIRED, SKIPPED -> EnumSet.noneOf(DecisionStatus.class);
    };
  }

  /**
   * Moves to another status.
   *
   * @param next the status to reach
   * @return {@code next}
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  public DecisionStatus moveTo(DecisionStatus next) {
    if (!allowed().contains(next)) {
      throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    return next;
  }
}
