package co.caudal.domain.device;

/**
 * A command waiting for its device: it may be delivered, expire or be cancelled.
 *
 * @pattern P18 State
 */
public final class PendingCommandState implements ValveCommandState {

  static final PendingCommandState INSTANCE = new PendingCommandState();

  private PendingCommandState() {}

  @Override
  public String status() {
    return PENDING;
  }

  @Override
  public ValveCommandState deliver() {
    return DeliveredCommandState.INSTANCE;
  }

  @Override
  public ValveCommandState expire() {
    return ExpiredCommandState.INSTANCE;
  }

  @Override
  public ValveCommandState cancel() {
    return CancelledCommandState.INSTANCE;
  }
}
