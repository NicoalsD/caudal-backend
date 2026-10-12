package co.caudal.domain.reading.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A reading that is about to be stored, with what the checks need to judge it.
 *
 * @param value the gauge value read
 * @param observedAt when it was read, or null if the client did not say
 * @param now the server clock
 * @param gaugeMin minimum of the painted gauge of the tank
 * @param gaugeMax maximum of the painted gauge of the tank
 * @param limits the limits of the active rules
 * @param ageExempt true for simulated history imported into a demo aqueduct, which may be old
 */
public record ReadingCandidate(
    BigDecimal value,
    Instant observedAt,
    Instant now,
    BigDecimal gaugeMin,
    BigDecimal gaugeMax,
    ReadingLimits limits,
    boolean ageExempt) {

  /** Validates the parts that every candidate has. */
  public ReadingCandidate {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(gaugeMin, "gaugeMin");
    Objects.requireNonNull(gaugeMax, "gaugeMax");
    Objects.requireNonNull(limits, "limits");
  }

  /**
   * The limits of the Board's rules that apply to readings.
   *
   * @param maxBackdateDays oldest reading accepted, in days
   * @param duplicateWindowMinutes window to flag a repeated value
   * @param maxLevelChangePerHour change per hour that flags a sudden jump
   */
  public record ReadingLimits(
      int maxBackdateDays, int duplicateWindowMinutes, BigDecimal maxLevelChangePerHour) {}
}
