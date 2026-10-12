package co.caudal.domain.device;

/**
 * A command withdrawn before delivery. It is terminal.
 *
 * @pattern P18 State
 */
public final class CancelledCommandState implements ValveCommandState {

  static final CancelledCommandState INSTANCE = new CancelledCommandState();

  private CancelledCommandState() {}

  @Override
  public String status() {
    return CANCELLED;
  }
}
