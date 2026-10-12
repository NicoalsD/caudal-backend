package co.caudal.application.device.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * A public key of a device that may still verify signatures.
 *
 * @param keyId the key
 * @param publicKey the key in base64url without padding
 */
public record DeviceKeyCredential(UUID keyId, String publicKey) {

  /** Validates the parts. */
  public DeviceKeyCredential {
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(publicKey, "publicKey");
  }
}
