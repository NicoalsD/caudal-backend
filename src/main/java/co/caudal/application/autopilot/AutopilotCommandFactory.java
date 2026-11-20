package co.caudal.application.autopilot;

import co.caudal.application.device.DeviceView;
import co.caudal.application.device.command.NewValveCommand;
import co.caudal.application.device.command.ValveCommandView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ValveCommandPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the valve command of a decision of the autopilot, both when a person accepts it and when
 * the autopilot executes it by itself. The command carries {@code autopilot_decision_id} as its
 * only origin, starts now and stays valid for the same window as the commands of a schedule. The
 * caller runs it inside a transaction scoped to the aqueduct.
 */
public class AutopilotCommandFactory {

  private final DevicePort devices;
  private final NetworkPort network;
  private final ValveCommandPort commands;
  private final Duration window;
  private final Clock clock;

  /**
   * Creates the factory.
   *
   * @param devices the actuators of the valves
   * @param network the valves
   * @param commands the valve commands
   * @param window how long a command stays valid
   * @param clock source of time
   */
  public AutopilotCommandFactory(
      DevicePort devices,
      NetworkPort network,
      ValveCommandPort commands,
      Duration window,
      Clock clock) {
    this.devices = devices;
    this.network = network;
    this.commands = commands;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Stores the command of a decision as PENDING.
   *
   * @param aqueductId the aqueduct
   * @param decision the decision, an OPEN or a CLOSE with its valve
   * @param createdBy the person who accepted it, or null when the autopilot executes it
   * @return the stored command
   * @throws DomainException with {@code NOT_FOUND} if the valve is not motorized or has no active
   *     actuator, and with {@code CONFLICT} if the valve already has an active command for the same
   *     moment
   */
  public ValveCommandView create(UUID aqueductId, DecisionView decision, UUID createdBy) {
    ValveView valve =
        network
            .valve(decision.valveId())
            .filter(ValveView::motorized)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    DeviceView actuator =
        devices
            .activeActuatorOf(valve.id())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Instant now = clock.instant();
    return commands
        .insert(
            new NewValveCommand(
                aqueductId,
                actuator.id(),
                valve.id(),
                decision.action(),
                null,
                null,
                decision.id(),
                createdBy,
                now,
                now.plus(window)))
        .orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, Map.of("field", "not_before")));
  }
}
