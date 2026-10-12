package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records a reading of the tank.
 *
 * @param caller who records it
 * @param id id chosen by the client (idempotency key)
 * @param tankId the tank
 * @param gaugeValue the value read
 * @param waterAppearanceCode the appearance of the water
 * @param damageNoticed true if damage was noticed
 * @param note free note, or null
 * @param observedAt when it was observed; null is refused by the handler
 */
public record CreateReadingCommand(
    Caller caller,
    UUID id,
    UUID tankId,
    BigDecimal gaugeValue,
    String waterAppearanceCode,
    boolean damageNoticed,
    String note,
    Instant observedAt)
    implements Command<RecordedReading> {

  /** Validates the parts. */
  public CreateReadingCommand {
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tankId, "tankId");
    Objects.requireNonNull(gaugeValue, "gaugeValue");
    Objects.requireNonNull(waterAppearanceCode, "waterAppearanceCode");
  }
}
