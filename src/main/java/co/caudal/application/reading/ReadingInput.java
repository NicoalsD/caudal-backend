package co.caudal.application.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What a client sends for one reading, alone or inside a batch.
 *
 * @param id the id chosen by the client, for idempotency
 * @param tankId the tank
 * @param gaugeValue the value read
 * @param waterAppearanceCode how the water looked
 * @param damageNoticed whether damage was seen
 * @param note the note, already trimmed, or null
 * @param observedAt when it was read, or null if the client did not say
 */
public record ReadingInput(
    UUID id,
    UUID tankId,
    BigDecimal gaugeValue,
    String waterAppearanceCode,
    boolean damageNoticed,
    String note,
    Instant observedAt) {

  /** Validates the parts that every reading has. */
  public ReadingInput {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tankId, "tankId");
    Objects.requireNonNull(gaugeValue, "gaugeValue");
    Objects.requireNonNull(waterAppearanceCode, "waterAppearanceCode");
  }
}
