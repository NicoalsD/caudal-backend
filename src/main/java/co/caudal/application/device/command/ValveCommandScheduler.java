package co.caudal.application.device.command;

import co.caudal.application.device.DeviceView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.ValveCommandPort;
import co.caudal.application.rules.RuleSetView.ValveOrderView;
import co.caudal.application.schedule.ItemView;
import co.caudal.domain.device.ValveCommandState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates the valve commands of a published schedule (docs/Protocolo-de-dispositivos.md, section
 * 5.1). For each shift and each motorized valve of its sector that has an active actuator, one
 * {@code OPEN} command may run from two minutes before the shift start, and one {@code CLOSE}
 * command from the shift end. Both expire ten minutes after their moment. The valves are taken in
 * the opening order of the active rule set.
 *
 * <p>The PENDING commands that other proposals of the same day left for this aqueduct are cancelled
 * first, each one with its history row. The caller runs this in the transaction of the publication.
 *
 * @pattern P18 State
 */
public final class ValveCommandScheduler {

  private static final String OPEN = "OPEN";
  private static final String CLOSE = "CLOSE";

  private final ValveCommandPort commands;
  private final DevicePort devices;
  private final NetworkPort network;
  private final RuleSetPort ruleSets;
  private final Duration openLead;
  private final Duration window;
  private final Clock clock;

  /**
   * Creates the scheduler.
   *
   * @param commands the valve commands
   * @param devices the actuators of the valves
   * @param network the valves of the sectors
   * @param ruleSets the active rule set, with the opening order of the valves
   * @param openLead how long before a shift its OPEN command may run
   * @param window how long a command stays valid after its moment
   * @param clock source of time
   */
  public ValveCommandScheduler(
      ValveCommandPort commands,
      DevicePort devices,
      NetworkPort network,
      RuleSetPort ruleSets,
      Duration openLead,
      Duration window,
      Clock clock) {
    this.commands = Objects.requireNonNull(commands, "commands");
    this.devices = Objects.requireNonNull(devices, "devices");
    this.network = Objects.requireNonNull(network, "network");
    this.ruleSets = Objects.requireNonNull(ruleSets, "ruleSets");
    this.openLead = Objects.requireNonNull(openLead, "openLead");
    this.window = Objects.requireNonNull(window, "window");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Cancels the pending commands of the other proposals of the day and creates the commands of the
   * shifts of this proposal.
   *
   * @param aqueductId the aqueduct
   * @param proposalId the proposal being published
   * @param day the service day
   * @param items the shifts of the proposal
   */
  public void schedule(UUID aqueductId, UUID proposalId, LocalDate day, List<ItemView> items) {
    Instant now = clock.instant();
    cancelPending(aqueductId, proposalId, day, now);
    Map<UUID, Integer> order = openingOrder();
    for (ItemView item : items) {
      for (ValveView valve : motorizedValves(item.sectorId(), order)) {
        Optional<DeviceView> actuator = devices.activeActuatorOf(valve.id());
        if (actuator.isEmpty()) {
          continue;
        }
        Instant open = item.startAt().minus(openLead);
        store(aqueductId, actuator.get(), valve, item, OPEN, open, item.startAt().plus(window));
        store(
            aqueductId,
            actuator.get(),
            valve,
            item,
            CLOSE,
            item.endAt(),
            item.endAt().plus(window));
      }
    }
  }

  /**
   * Moves every pending command of the other proposals of the day to CANCELLED, with its event. A
   * lost race leaves the command as it is.
   */
  private void cancelPending(UUID aqueductId, UUID proposalId, LocalDate day, Instant now) {
    for (ValveCommandView command : commands.pendingOfDay(aqueductId, day, proposalId)) {
      ValveCommandState next = ValveCommandState.of(command.status()).cancel();
      if (commands.changeStatus(command.id(), command.status(), next.status())) {
        commands.insertEvent(aqueductId, command.id(), next.status(), null, now);
      }
    }
  }

  /** The valves of the sector are opened in the order of the rule set; the others come last. */
  private List<ValveView> motorizedValves(UUID sectorId, Map<UUID, Integer> order) {
    return network.valves(sectorId).stream()
        .filter(ValveView::motorized)
        .sorted(
            Comparator.comparingInt((ValveView v) -> order.getOrDefault(v.id(), Integer.MAX_VALUE))
                .thenComparing(ValveView::code))
        .toList();
  }

  private Map<UUID, Integer> openingOrder() {
    return ruleSets.findActive().map(rules -> rankOf(rules.valveOrder())).orElse(Map.of());
  }

  private static Map<UUID, Integer> rankOf(List<ValveOrderView> valveOrder) {
    return valveOrder.stream()
        .collect(
            Collectors.toMap(
                ValveOrderView::valveId, ValveOrderView::sequenceOrder, (first, second) -> first));
  }

  /**
   * Stores one command of a shift. A moment already taken by an active command of the valve is not
   * stored again (docs/Protocolo-de-dispositivos.md, section 5.5).
   */
  private void store(
      UUID aqueductId,
      DeviceView actuator,
      ValveView valve,
      ItemView item,
      String command,
      Instant notBefore,
      Instant expiresAt) {
    commands.insert(
        new NewValveCommand(
            aqueductId,
            actuator.id(),
            valve.id(),
            command,
            item.id(),
            null,
            null,
            notBefore,
            expiresAt));
  }
}
