package co.caudal.domain.device;

/**
 * A command the device received and has not confirmed. Delivering it again is allowed (delivery is
 * at least once), so {@link #deliver()} returns the same state. It may be confirmed or expire.
 *
 * @pattern P18 State
 */
public final class DeliveredCommandState implements ValveCommandState {

  static final DeliveredCommandState INSTANCE = new DeliveredCommandState();

  private DeliveredCommandState() {}

  @Override
  public String status() {
    return DELIVERED;
  }

  @Override
  public ValveCommandState deliver() {
    return this;
  }

  @Override
  public ValveCommandState ack() {
    return AckedCommandState.INSTANCE;
  }

  @Override
  public ValveCommandState fail() {
    return FailedCommandState.INSTANCE;
  }

  @Override
  public ValveCommandState expire() {
    return ExpiredCommandState.INSTANCE;
  }
}
