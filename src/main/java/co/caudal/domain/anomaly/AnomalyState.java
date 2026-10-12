package co.caudal.domain.anomaly;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Life of an anomaly (docs/Reglas-de-negocio.md, section 11): a suspicion stays {@code OPEN} until
 * a person confirms it in the field or dismisses it, and then it no longer changes. Dismissing
 * needs the reason, so nobody hides a warning without saying why.
 *
 * @pattern P18 State
 */
public sealed interface AnomalyState
    permits AnomalyState.Open, AnomalyState.Confirmed, AnomalyState.Dismissed {

  /**
   * Restores the state from {@code ops.anomalies.status}.
   *
   * @param status the stored status
   * @return the state
   * @throws IllegalArgumentException for an unknown status
   */
  static AnomalyState of(String status) {
    return switch (status) {
      case "OPEN" -> new Open();
      case "CONFIRMED" -> new Confirmed();
      case "DISMISSED" -> new Dismissed();
      default -> throw new IllegalArgumentException("Unknown anomaly status");
    };
  }

  /**
   * The stored code.
   *
   * @return {@code OPEN}, {@code CONFIRMED} or {@code DISMISSED}
   */
  String code();

  /**
   * Resolves the anomaly.
   *
   * @param target {@code CONFIRMED} or {@code DISMISSED}
   * @param note the note of the person, possibly null
   * @return the new state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} when it is not open or the target
   *     is unknown, or {@code REASON_REQUIRED} when a dismissal has no reason
   */
  default AnomalyState resolve(String target, String note) {
    throw new DomainException(
        ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", code(), "to", String.valueOf(target)));
  }

  /** A suspicion waiting for a person. */
  record Open() implements AnomalyState {
    @Override
    public String code() {
      return "OPEN";
    }

    @Override
    public AnomalyState resolve(String target, String note) {
      if ("CONFIRMED".equals(target)) {
        return new Confirmed();
      }
      if ("DISMISSED".equals(target)) {
        int length = note == null ? 0 : note.strip().length();
        if (length < FieldLimits.REASON_MIN || length > FieldLimits.REASON_MAX) {
          throw new DomainException(ErrorCode.REASON_REQUIRED);
        }
        return new Dismissed();
      }
      return AnomalyState.super.resolve(target, note);
    }
  }

  /** Seen in the field: it was real. */
  record Confirmed() implements AnomalyState {
    @Override
    public String code() {
      return "CONFIRMED";
    }
  }

  /** Checked and discarded, with the reason. */
  record Dismissed() implements AnomalyState {
    @Override
    public String code() {
      return "DISMISSED";
    }
  }
}
