package co.caudal.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the field devices (docs/Protocolo-de-dispositivos.md, sections 1.3 and 3.1).
 *
 * @param keyRotationDays days the previous key stays valid after a rotation ({@code
 *     DEVICE_KEY_ROTATION_DAYS}); zero revokes it at once
 * @param requestsPerMinute signed requests a device may make per minute ({@code
 *     DEVICE_REQUESTS_PER_MINUTE}, 60 by default)
 * @param valveOpenLeadMinutes minutes before a shift when its OPEN command may run ({@code
 *     DEVICE_VALVE_OPEN_LEAD_MINUTES}, 2 by default)
 * @param valveCommandWindowMinutes minutes after the moment of a valve command when it expires
 *     ({@code DEVICE_VALVE_COMMAND_WINDOW_MINUTES}, 10 by default)
 */
@ConfigurationProperties(prefix = "caudal.devices")
public record DeviceProperties(
    int keyRotationDays,
    int requestsPerMinute,
    int valveOpenLeadMinutes,
    int valveCommandWindowMinutes) {

  /**
   * Refuses a negative period and a rate that allows no request.
   *
   * @throws IllegalArgumentException if the period is negative or the rate is below one
   */
  public DeviceProperties {
    if (keyRotationDays < 0) {
      throw new IllegalArgumentException("Key rotation days must not be negative");
    }
    if (requestsPerMinute < 1) {
      throw new IllegalArgumentException("Requests per minute must be at least one");
    }
    if (valveOpenLeadMinutes < 0 || valveCommandWindowMinutes < 1) {
      throw new IllegalArgumentException("Valve command minutes must be positive");
    }
  }
}
