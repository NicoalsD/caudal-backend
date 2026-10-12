package co.caudal.application.device.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * What the steps of the signature check know while they run: the request, the moment of the check,
 * the device as far as it is known and the key that verified the signature.
 *
 * <p>The authenticator fills the device before the first step runs, so an unknown device is a null
 * credentials and the first step refuses it.
 */
public final class DeviceAuthContext {

  private final SignedRequest request;
  private final Instant now;
  private final DeviceCredentials credentials;
  private UUID matchedKeyId;

  /**
   * Starts a check.
   *
   * @param request the request
   * @param now the moment of the check
   * @param credentials the device, or null if it does not exist
   */
  public DeviceAuthContext(SignedRequest request, Instant now, DeviceCredentials credentials) {
    this.request = request;
    this.now = now;
    this.credentials = credentials;
  }

  /**
   * The request being checked.
   *
   * @return the request
   */
  public SignedRequest request() {
    return request;
  }

  /**
   * The moment of the check.
   *
   * @return the instant
   */
  public Instant now() {
    return now;
  }

  /**
   * The device, if it exists.
   *
   * @return the credentials, or null for an unknown device
   */
  public DeviceCredentials credentials() {
    return credentials;
  }

  /**
   * The key that verified the signature, set by the signature step.
   *
   * @return the key, or null before the signature is verified
   */
  public UUID matchedKeyId() {
    return matchedKeyId;
  }

  /**
   * Records the key that verified the signature.
   *
   * @param keyId the key
   */
  void matchKey(UUID keyId) {
    this.matchedKeyId = keyId;
  }
}
