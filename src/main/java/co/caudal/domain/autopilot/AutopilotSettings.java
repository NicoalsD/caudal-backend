package co.caudal.domain.autopilot;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration of the autopilot of one aqueduct: the mode, how long the operator may be absent
 * before the autopilot acts, and the reserve floor under which it never opens a valve.
 *
 * @param aqueductId the aqueduct
 * @param mode the mode
 * @param operatorAbsenceMinutes minutes without human action before {@code AUTOMATIC} acts
 * @param reserveFloorPct tank level, in percent, at or under which an OPEN is never executed
 * @param updatedAt when it last changed
 */
public record AutopilotSettings(
    UUID aqueductId,
    AutopilotMode mode,
    int operatorAbsenceMinutes,
    BigDecimal reserveFloorPct,
    Instant updatedAt) {

  /** Validates the values against the limits of {@link FieldLimits}. */
  public AutopilotSettings {
    Objects.requireNonNull(aqueductId, "aqueductId");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(reserveFloorPct, "reserveFloorPct");
    if (operatorAbsenceMinutes < FieldLimits.OPERATOR_ABSENCE_MINUTES_MIN
        || operatorAbsenceMinutes > FieldLimits.OPERATOR_ABSENCE_MINUTES_MAX) {
      throw invalid("operator_absence_minutes");
    }
    if (reserveFloorPct.compareTo(BigDecimal.valueOf(FieldLimits.RESERVE_FLOOR_PCT_MIN)) < 0
        || reserveFloorPct.compareTo(BigDecimal.valueOf(FieldLimits.RESERVE_FLOOR_PCT_MAX)) > 0) {
      throw invalid("reserve_floor_pct");
    }
  }

  /**
   * The same settings with new values; they are validated like any other.
   *
   * @param newMode the mode
   * @param newAbsenceMinutes the minutes of absence
   * @param newReserveFloorPct the reserve floor, in percent
   * @return the changed settings
   * @throws DomainException with {@code VALIDATION_ERROR} if a value is out of its limits
   */
  public AutopilotSettings change(
      AutopilotMode newMode, int newAbsenceMinutes, BigDecimal newReserveFloorPct) {
    return new AutopilotSettings(
        aqueductId, newMode, newAbsenceMinutes, newReserveFloorPct, updatedAt);
  }

  private static DomainException invalid(String field) {
    return new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", field));
  }
}
