package co.caudal.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the field devices (docs/Protocolo-de-dispositivos.md, section 1.3).
 *
 * @param keyRotationDays days the previous key stays valid after a rotation ({@code
 *     DEVICE_KEY_ROTATION_DAYS}); zero revokes it at once
 */
@ConfigurationProperties(prefix = "caudal.devices")
public record DeviceProperties(int keyRotationDays) {

  /**
   * Refuses a negative period.
   *
   * @throws IllegalArgumentException if it is negative
   */
  public DeviceProperties {
    if (keyRotationDays < 0) {
      throw new IllegalArgumentException("Key rotation days must not be negative");
    }
  }
}
