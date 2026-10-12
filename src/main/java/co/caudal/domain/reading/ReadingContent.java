package co.caudal.domain.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * What a person reported in a reading, without what the server added. Two requests with the same
 * client id and the same content are one reading sent twice, for example by the offline queue after
 * a lost answer (docs/Reglas-de-negocio.md, section 8.2).
 *
 * @param tankId the tank
 * @param gaugeValue the value read
 * @param waterAppearanceCode how the water looked
 * @param damageNoticed whether damage was seen
 * @param note the note, or null
 * @param observedAt when it was read
 */
public record ReadingContent(
    UUID tankId,
    BigDecimal gaugeValue,
    String waterAppearanceCode,
    boolean damageNoticed,
    String note,
    Instant observedAt) {

  /**
   * Tells whether another report is the same reading. Values compare by number (2.1 equals 2.10)
   * and moments to the microsecond, the precision PostgreSQL keeps.
   *
   * @param other the other report
   * @return true if it is the same reading
   */
  public boolean sameAs(ReadingContent other) {
    return tankId.equals(other.tankId)
        && gaugeValue.compareTo(other.gaugeValue) == 0
        && waterAppearanceCode.equals(other.waterAppearanceCode)
        && damageNoticed == other.damageNoticed
        && Objects.equals(note, other.note)
        && observedAt != null
        && other.observedAt != null
        && micros(observedAt).equals(micros(other.observedAt));
  }

  private static Instant micros(Instant moment) {
    return moment.truncatedTo(ChronoUnit.MICROS);
  }
}
