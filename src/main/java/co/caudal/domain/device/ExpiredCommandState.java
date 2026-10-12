package co.caudal.domain.device;

/**
 * A command that passed its expiration. It is terminal: it is never delivered nor confirmed.
 *
 * @pattern P18 State
 */
public final class ExpiredCommandState implements ValveCommandState {

  static final ExpiredCommandState INSTANCE = new ExpiredCommandState();

  private ExpiredCommandState() {}

  @Override
  public String status() {
    return EXPIRED;
  }
}
