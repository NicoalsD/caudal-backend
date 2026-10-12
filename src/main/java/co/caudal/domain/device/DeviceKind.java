package co.caudal.domain.device;

/**
 * Kinds of field device (docs/API.md, section 16.4). Each kind points at one part of the network: a
 * level sensor at a tank, a valve actuator at a valve, and a gateway at nothing.
 */
public enum DeviceKind {

  /** Reads the level of a tank. */
  LEVEL_SENSOR(true, false),

  /** Moves a valve. */
  VALVE_ACTUATOR(false, true),

  /** Relays the messages of other devices; it belongs to no part of the network. */
  GATEWAY(false, false);

  private final boolean tankRequired;
  private final boolean valveRequired;

  DeviceKind(boolean tankRequired, boolean valveRequired) {
    this.tankRequired = tankRequired;
    this.valveRequired = valveRequired;
  }

  /**
   * Tells whether the device must name a tank.
   *
   * @return true for a level sensor
   */
  public boolean tankRequired() {
    return tankRequired;
  }

  /**
   * Tells whether the device must name a valve.
   *
   * @return true for a valve actuator
   */
  public boolean valveRequired() {
    return valveRequired;
  }
}
