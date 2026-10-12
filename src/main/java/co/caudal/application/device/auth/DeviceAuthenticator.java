package co.caudal.application.device.auth;

import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.FieldLimits;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks the signature of a device request in the order of docs/Protocolo-de-dispositivos.md,
 * section 3.1. The device is read first (outside the checks, so that a failure can still be
 * recorded), the steps run in order and the first refusal stops them.
 *
 * <p>The security events of section 3.2 are recorded in their own transaction before the refusal is
 * thrown, so they survive it. Ten invalid signatures of one device within the repeated-failure
 * window add a {@code REPEATED} event.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class DeviceAuthenticator {

  /** Reason of the event for the repeated invalid signatures. */
  static final String REPEATED = "REPEATED";

  private final DevicePort devices;
  private final SecurityEventPort events;
  private final UnitOfWorkPort unitOfWork;
  private final PrivacyHasherPort hasher;
  private final Clock clock;
  private final DeviceAuthHandler chain;

  /**
   * Creates the authenticator with its chain of steps.
   *
   * @param devices the devices and their keys
   * @param events the security events
   * @param unitOfWork transaction boundary
   * @param hasher keyed hash of the address of the connection
   * @param clock source of time
   */
  public DeviceAuthenticator(
      DevicePort devices,
      SecurityEventPort events,
      UnitOfWorkPort unitOfWork,
      PrivacyHasherPort hasher,
      Clock clock) {
    this.devices = devices;
    this.events = events;
    this.unitOfWork = unitOfWork;
    this.hasher = hasher;
    this.clock = clock;
    this.chain = chain();
  }

  /**
   * Verifies the signature of a request.
   *
   * @param request the request with its headers parsed
   * @return the device that signed it
   * @throws DeviceAuthFailure at the first step that refuses the request
   */
  public AuthenticatedDevice authenticate(SignedRequest request) {
    Optional<DeviceCredentials> device =
        unitOfWork.execute(() -> devices.credentials(request.deviceId()));
    DeviceAuthContext context =
        new DeviceAuthContext(request, clock.instant(), device.orElse(null));
    try {
      chain.handle(context);
    } catch (DeviceAuthFailure failure) {
      throw reject(context, failure);
    }
    DeviceCredentials verified = context.credentials();
    return new AuthenticatedDevice(
        verified.deviceId(),
        verified.aqueductId(),
        verified.kind(),
        verified.tankId(),
        verified.valveId(),
        context.matchedKeyId());
  }

  /** Records the event of a refusal, then returns the failure to throw after the commit. */
  private DeviceAuthFailure reject(DeviceAuthContext context, DeviceAuthFailure failure) {
    if (failure.eventType() == null) {
      return failure;
    }
    DeviceCredentials device = context.credentials();
    UUID deviceId = device == null ? null : device.deviceId();
    UUID aqueductId = device == null ? null : device.aqueductId();
    String ipHmac =
        context.request().ipAddress() == null ? null : hasher.hmac(context.request().ipAddress());
    Instant since = context.now().minusSeconds(FieldLimits.DEVICE_REPEATED_FAILURES_WINDOW_SECONDS);
    unitOfWork.execute(
        () -> {
          events.record(
              new SecurityEvent(
                  failure.eventType(),
                  failure.severity(),
                  null,
                  aqueductId,
                  ipHmac,
                  Map.of("reason", failure.reason()),
                  deviceId));
          if (deviceId != null
              && failure.eventType() == SecurityEventType.INVALID_SIGNATURE
              && devices.countSecurityEvents(deviceId, SecurityEventType.INVALID_SIGNATURE, since)
                  >= FieldLimits.DEVICE_REPEATED_FAILURES) {
            events.record(
                new SecurityEvent(
                    SecurityEventType.INVALID_SIGNATURE,
                    Severity.HIGH,
                    null,
                    aqueductId,
                    ipHmac,
                    Map.of("reason", REPEATED),
                    deviceId));
          }
          return Boolean.TRUE;
        });
    return failure;
  }

  private static DeviceAuthHandler chain() {
    DeviceAuthHandler first = new ActiveDeviceHandler();
    first
        .linkWith(new ValidKeyHandler())
        .linkWith(new TimestampWindowHandler())
        .linkWith(new SignatureHandler());
    return first;
  }
}
