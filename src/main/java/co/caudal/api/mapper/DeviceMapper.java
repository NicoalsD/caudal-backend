package co.caudal.api.mapper;

import co.caudal.api.dto.response.DeviceKeyResponse;
import co.caudal.api.dto.response.DeviceResponse;
import co.caudal.application.device.DeviceKeyView;
import co.caudal.application.device.DeviceView;

/** Maps field devices to response DTOs. */
public final class DeviceMapper {

  private DeviceMapper() {}

  /**
   * Maps a device.
   *
   * @param device the device
   * @return the response
   */
  public static DeviceResponse toResponse(DeviceView device) {
    return new DeviceResponse(
        device.id(),
        device.kind(),
        device.name(),
        device.tankId(),
        device.valveId(),
        device.status(),
        device.firmwareVersion(),
        device.lastSeenAt());
  }

  /**
   * Maps a registered key.
   *
   * @param key the key
   * @return the response
   */
  public static DeviceKeyResponse toResponse(DeviceKeyView key) {
    return new DeviceKeyResponse(key.keyId(), key.fingerprint(), key.activatedAt());
  }
}
