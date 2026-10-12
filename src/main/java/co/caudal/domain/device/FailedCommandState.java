package co.caudal.domain.device;

/**
 * A command the device confirmed as failed. Confirming it again as failed is idempotent; any other
 * move is refused.
 *
 * @pattern P18 State
 */
public final class FailedCommandState implements ValveCommandState {

  static final FailedCommandState INSTANCE = new FailedCommandState();

  private FailedCommandState() {}

  @Override
  public String status() {
    return FAILED;
  }

  @Override
  public ValveCommandState fail() {
    return this;
  }
}
