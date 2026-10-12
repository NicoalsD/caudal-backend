package co.caudal.application.device.auth;

/**
 * One step of the signature check (docs/Protocolo-de-dispositivos.md, section 3.1). A step either
 * lets the request go on to the next step or refuses it with a {@link DeviceAuthFailure}, and then
 * no later step runs.
 *
 * @pattern P12 Chain of Responsibility
 */
public abstract class DeviceAuthHandler {

  private DeviceAuthHandler next;

  /**
   * Links the step that follows this one.
   *
   * @param following the next step
   * @return the next step, so that the chain can be built in one line
   */
  public DeviceAuthHandler linkWith(DeviceAuthHandler following) {
    this.next = following;
    return following;
  }

  /**
   * Runs this step and, if it passes, the rest of the chain.
   *
   * @param context the check in progress
   * @throws DeviceAuthFailure if a step refuses the request
   */
  public final void handle(DeviceAuthContext context) {
    check(context);
    if (next != null) {
      next.handle(context);
    }
  }

  /**
   * The check of this step.
   *
   * @param context the check in progress
   * @throws DeviceAuthFailure if the request does not pass this step
   */
  protected abstract void check(DeviceAuthContext context);
}
