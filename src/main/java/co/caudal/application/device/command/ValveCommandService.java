package co.caudal.application.device.command;

import co.caudal.application.device.auth.AuthenticatedDevice;
import co.caudal.application.device.auth.DeviceAuthenticator;
import co.caudal.application.device.auth.SignedRequest;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.port.out.CommandConfirmationPayloadPort;
import co.caudal.application.port.out.ValveCommandPort;
import co.caudal.domain.device.ValveCommandState;
import co.caudal.domain.event.ValveCommandConfirmed;
import co.caudal.domain.event.ValveCommandDelivered;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
  private final CommandConfirmationPayloadPort payloads;
  private final DomainEventPublisher events;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param authenticator checks the signature and runs the work in its transaction
   * @param commands the valve commands
   * @param payloads reads the body of a confirmation
   * @param events publishes the domain events
   * @param clock source of time
   */
  public ValveCommandService(
      DeviceAuthenticator authenticator,
      ValveCommandPort commands,
      CommandConfirmationPayloadPort payloads,
      DomainEventPublisher events,
      Clock clock) {
    this.authenticator = authenticator;
    this.commands = commands;
    this.payloads = payloads;
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

  /**
   * Verifies a signed confirmation of a valve command and records it (docs/API.md, section 16.3). A
   * confirmation that repeats the status the command already has is answered as it is, without a
   * new event. A confirmation that the lifecycle does not allow is refused.
   *
   * @param request the signed request, with its raw body
   * @param commandId the command, as the path sent it
   * @return the command with its status after the confirmation
   * @throws DomainException with {@code NOT_FOUND} if the command is not one of the device, and
   *     with {@code INVALID_STATE_TRANSITION} if the lifecycle does not allow the confirmation
   */
  public ValveCommandView confirm(SignedRequest request, String commandId) {
    return authenticator.authenticate(
        request, device -> confirmation(device, commandId, payloads.parse(request.body())));
  }

  private ValveCommandView confirmation(
      AuthenticatedDevice device, String commandId, CommandConfirmation confirmation) {
    UUID id = commandOf(commandId);
    ValveCommandView command =
        commands
            .find(id, device.deviceId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    ValveCommandState current = ValveCommandState.of(command.status());
    if (current.status().equals(confirmation.result())) {
      return command;
    }
    ValveCommandState next =
        ValveCommandState.ACKED.equals(confirmation.result()) ? current.ack() : current.fail();
    Instant now = clock.instant();
    if (!commands.changeStatus(id, current.status(), next.status())) {
      throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    commands.insertEvent(command.aqueductId(), id, next.status(), confirmation.detail(), now);
    events.publish(
        new ValveCommandConfirmed(command.aqueductId(), id, device.deviceId(), next.status(), now));
    return command.withStatus(next.status());
  }

  /** The command of the path; a text that is not an identifier names no command. */
  private static UUID commandOf(String text) {
    if (!text.matches(FieldLimits.DEVICE_ID_PATTERN)) {
      throw new DomainException(ErrorCode.NOT_FOUND);
    }
    return UUID.fromString(text);
  }
}
