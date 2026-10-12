package co.caudal.application.device;

import java.time.Instant;
import java.util.UUID;

/**
 * A public key of a device.
 *
 * @param keyId the key
 * @param fingerprint SHA-256 of the key, in lower case hexadecimal
 * @param activatedAt when it was registered
 */
public record DeviceKeyView(UUID keyId, String fingerprint, Instant activatedAt) {}
