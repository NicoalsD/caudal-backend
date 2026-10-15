package co.caudal.application.device;

import java.time.Instant;
import java.util.UUID;

/**
 * The validity of the key of a device that still accepts signatures. The key material is never read
 * for listings.
 *
 * @param id the key
 * @param validFrom when the key was registered
 * @param validUntil when the key stops being accepted, or null if it is open-ended
 */
public record DeviceKeyValidityView(UUID id, Instant validFrom, Instant validUntil) {}
