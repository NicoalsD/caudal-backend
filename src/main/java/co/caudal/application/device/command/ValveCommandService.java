package co.caudal.application.device.command;

import co.caudal.application.device.auth.AuthenticatedDevice;
import co.caudal.application.device.auth.DeviceAuthenticator;
import co.caudal.application.device.auth.SignedRequest;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.port.out.ValveCommandPort;
import co.caudal.domain.device.ValveCommandState;
import co.caudal.domain.event.ValveCommandDelivered;
import co.caudal.shared.FieldLimits;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hands the valve commands to their actuator (docs/API.md, section 16.2 and
 * docs/Protocolo-de-dispositivos.md, sections 5.3 and 5.4). A command waiting for its device and
 * due becomes {@code DELIVERED} with its history row. A command already delivered is sent again,
 * since delivery is at least once. A command past its expiration becomes {@code EXPIRED} and is
 * never sent.
 *
 * <p>Every transition is a {@link ValveCommandState} move, so the rules of the lifecycle live in
 * the domain.
 *
 * @pattern P18 State
 */
public final class ValveCommandService {

  private final DeviceAuthenticator authenticator;
  private final ValveCommandPort commands;
  private final DomainEventPublisher events;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param authenticator checks the signature and runs the work in its transaction
   * @param commands the valve commands
   * @param events publishes the domain events
   * @param clock source of time
   */
  public ValveCommandService(
      DeviceAuthenticator authenticator,
      ValveCommandPort commands,
      DomainEventPublisher events,
      Clock clock) {
    this.authenticator = authenticator;
    this.commands = commands;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Verifies a signed request of a device and returns the commands it must run now.
   *
   * @param request the signed request of the device
   * @return up to 100 commands due now, ordered by {@code not_before}
   */
  public List<ValveCommandView> deliver(SignedRequest request) {
    return authenticator.authenticate(request, this::deliverDue);
  }

  private List<ValveCommandView> deliverDue(AuthenticatedDevice device) {
    Instant now = clock.instant();
    List<ValveCommandView> due = new ArrayList<>();
    for (ValveCommandView command : commands.open(device.deviceId())) {
      if (!command.expiresAt().isAfter(now)) {
        expire(command, now);
      } else if (!command.notBefore().isAfter(now)
          && due.size() < FieldLimits.DEVICE_COMMANDS_MAX) {
        handOver(command, now).ifPresent(due::add);
      }
    }
    return due;
  }

  /** Moves a command past its expiration to {@code EXPIRED}. A lost race leaves it as it is. */
  private void expire(ValveCommandView command, Instant now) {
    ValveCommandState next = ValveCommandState.of(command.status()).expire();
    if (commands.changeStatus(command.id(), command.status(), next.status())) {
      commands.insertEvent(command.aqueductId(), command.id(), next.status(), null, now);
    }
  }

  /**
   * Hands a due command to the device. A command already delivered is returned again without a new
   * event; a lost race leaves the command out of this answer.
   */
  private Optional<ValveCommandView> handOver(ValveCommandView command, Instant now) {
    ValveCommandState current = ValveCommandState.of(command.status());
    ValveCommandState next = current.deliver();
    if (next.status().equals(current.status())) {
      return Optional.of(command);
    }
    if (!commands.changeStatus(command.id(), current.status(), next.status())) {
      return Optional.empty();
    }
    commands.insertEvent(command.aqueductId(), command.id(), next.status(), null, now);
    events.publish(
        new ValveCommandDelivered(command.aqueductId(), command.id(), command.deviceId(), now));
    return Optional.of(command.withStatus(next.status()));
  }
}
