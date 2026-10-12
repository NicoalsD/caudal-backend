package co.caudal.domain.device;

/**
 * A command the device confirmed as done. Confirming it again as done is idempotent; any other move
 * is refused.
 *
 * @pattern P18 State
 */
public final class AckedCommandState implements ValveCommandState {

  static final AckedCommandState INSTANCE = new AckedCommandState();

  private AckedCommandState() {}

  @Override
  public String status() {
    return ACKED;
  }

  @Override
  public ValveCommandState ack() {
    return this;
  }
}
