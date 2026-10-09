package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Creates a tank in the aqueduct of the session.
 *
 * @param caller who creates it
 * @param name the name
 * @param gaugeMin minimum of the gauge
 * @param gaugeMax maximum of the gauge
 * @param gaugeStep reading precision
 * @param capacityLiters capacity, or null
 */
public record CreateTankCommand(
    Caller caller,
    String name,
    BigDecimal gaugeMin,
    BigDecimal gaugeMax,
    BigDecimal gaugeStep,
    Integer capacityLiters)
    implements Command<TankView> {

  /** Validates the parts. */
  public CreateTankCommand {
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(gaugeMin, "gaugeMin");
    Objects.requireNonNull(gaugeMax, "gaugeMax");
    Objects.requireNonNull(gaugeStep, "gaugeStep");
  }
}
