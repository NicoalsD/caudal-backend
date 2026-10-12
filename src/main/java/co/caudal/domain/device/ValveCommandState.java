package co.caudal.domain.device;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * State of a valve command (docs/Protocolo-de-dispositivos.md, section 5.2). A command is born
 * {@code PENDING}, is delivered to its device, and is confirmed or expires. Each state says which
 * moves it allows; every other move is refused with {@code INVALID_STATE_TRANSITION}.
 *
 * @pattern P18 State
 */
public sealed interface ValveCommandState
    permits PendingCommandState,
        DeliveredCommandState,
        AckedCommandState,
        FailedCommandState,
        ExpiredCommandState,
        CancelledCommandState {

  /** Code stored in {@code devices.valve_commands.status} for a command not yet delivered. */
  String PENDING = "PENDING";

  /** Code stored for a command the device has received and not confirmed yet. */
  String DELIVERED = "DELIVERED";

  /** Code stored for a command the device confirmed as done. */
  String ACKED = "ACKED";

  /** Code stored for a command the device confirmed as failed. */
  String FAILED = "FAILED";

  /** Code stored for a command that passed its expiration without being confirmed. */
  String EXPIRED = "EXPIRED";

  /** Code stored for a command withdrawn before delivery. */
  String CANCELLED = "CANCELLED";

  /**
   * Restores the state from the stored status.
   *
   * @param status the value of {@code status}
   * @return the state
   * @throws IllegalArgumentException if the status is unknown
   */
  static ValveCommandState of(String status) {
    return switch (status) {
      case PENDING -> PendingCommandState.INSTANCE;
      case DELIVERED -> DeliveredCommandState.INSTANCE;
      case ACKED -> AckedCommandState.INSTANCE;
      case FAILED -> FailedCommandState.INSTANCE;
      case EXPIRED -> ExpiredCommandState.INSTANCE;
      case CANCELLED -> CancelledCommandState.INSTANCE;
      default -> throw new IllegalArgumentException("Unknown valve command status");
    };
  }

  /**
   * The code stored in {@code devices.valve_commands.status}.
   *
   * @return the code
   */
  String status();

  /**
   * The device received the command.
   *
   * @return the next state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  default ValveCommandState deliver() {
    throw invalidTransition();
  }

  /**
   * The device confirmed the command as done.
   *
   * @return the next state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  default ValveCommandState ack() {
    throw invalidTransition();
  }

  /**
   * The device confirmed that the command failed.
   *
   * @return the next state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  default ValveCommandState fail() {
    throw invalidTransition();
  }

  /**
   * The command passed its expiration.
   *
   * @return the next state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  default ValveCommandState expire() {
    throw invalidTransition();
  }

  /**
   * The command was withdrawn before delivery.
   *
   * @return the next state
   * @throws DomainException with {@code INVALID_STATE_TRANSITION} if the move is not allowed
   */
  default ValveCommandState cancel() {
    throw invalidTransition();
  }

  private static DomainException invalidTransition() {
    return new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
  }
}
