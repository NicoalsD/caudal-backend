package co.caudal.application.device.auth;

import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Authenticates the signed requests of the devices in the order of
 * docs/Protocolo-de-dispositivos.md, section 3.1, and then runs the work of the request with the
 * device in the scope of its aqueduct.
 *
 * <p>The checks run in a chain (steps 1 to 5, outside a transaction, after a read of the device and
 * its nonce). Once the signature is valid, a first transaction stores the nonce (step 5b) and
 * applies the rate limit (step 7). It commits before the work runs, so a request refused later
 * (400, 404, 409...) keeps its nonce and cannot be replayed inside the window. The work runs in a
 * second transaction. A refusal of the first transaction is returned, not thrown, so that its
 * security event commits before the failure is thrown.
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

  /** Reason of the event for the rate limit (docs/Protocolo-de-dispositivos.md, section 3.2). */
  static final String DEVICE_LIMIT = "device";

  /** Prefix of the key of the rate limit of a device. */
  static final String RATE_KEY_PREFIX = "device:";

  private static final String RETRY_AFTER = "retry_after_seconds";

  private final DevicePort devices;
  private final SecurityEventPort events;
  private final UnitOfWorkPort unitOfWork;
  private final PrivacyHasherPort hasher;
  private final RateLimiterPort rateLimiter;
  private final RateLimit deviceLimit;
  private final Clock clock;
  private final DeviceAuthHandler chain;

  /**
   * Creates the authenticator with its chain of steps.
   *
   * @param devices the devices and their keys
   * @param events the security events
   * @param unitOfWork transaction boundary
   * @param hasher keyed hash of the address of the connection
   * @param rateLimiter the shared counter of requests
   * @param requestsPerMinute the requests a device may make per minute
   * @param clock source of time
   */
  public DeviceAuthenticator(
      DevicePort devices,
      SecurityEventPort events,
      UnitOfWorkPort unitOfWork,
      PrivacyHasherPort hasher,
      RateLimiterPort rateLimiter,
      int requestsPerMinute,
      Clock clock) {
    this.devices = devices;
    this.events = events;
    this.unitOfWork = unitOfWork;
    this.hasher = hasher;
    this.rateLimiter = rateLimiter;
    this.deviceLimit =
        new RateLimit(
            requestsPerMinute, Duration.ofSeconds(FieldLimits.DEVICE_RATE_LIMIT_WINDOW_SECONDS));
    this.clock = clock;
    this.chain = chain();
  }

  /**
   * Verifies the signature of a request and returns the device.
   *
   * @param request the request with its headers parsed
   * @return the device that signed it
   * @throws DeviceAuthFailure at the first refusal
   */
  public AuthenticatedDevice authenticate(SignedRequest request) {
    return authenticate(request, device -> device);
  }

  /**
   * Verifies the signature of a request, stores its nonce and applies the rate limit in a
   * transaction that commits, and then runs the work of the request in its own transaction.
   *
   * @param request the request with its headers parsed
   * @param work the work of the request, run once the request is accepted
   * @param <T> the result of the work
   * @return what the work returned
   * @throws DeviceAuthFailure at the first refusal, after its event is stored
   */
  public <T> T authenticate(SignedRequest request, Function<AuthenticatedDevice, T> work) {
    Instant now = clock.instant();
    Lookup found =
        unitOfWork.execute(
            () ->
                new Lookup(
                    devices.credentials(request.deviceId()),
                    devices.nonceSeenSince(
                        request.deviceId(),
                        request.nonce(),
                        now.minusSeconds(FieldLimits.DEVICE_NONCE_TTL_SECONDS))));
    DeviceAuthContext context =
        new DeviceAuthContext(request, now, found.device().orElse(null), found.nonceSeen());
    try {
      chain.handle(context);
    } catch (DeviceAuthFailure failure) {
      unitOfWork.execute(
          () -> {
            recordRefusal(context, failure);
            return Boolean.TRUE;
          });
      throw failure;
    }
    DeviceCredentials verified = context.credentials();
    AuthenticatedDevice device =
        new AuthenticatedDevice(
            verified.deviceId(),
            verified.aqueductId(),
            verified.kind(),
            verified.tankId(),
            verified.valveId(),
            context.matchedKeyId());
    SessionScope scope = new SessionScope(null, device.aqueductId());
    DeviceAuthFailure refusal = unitOfWork.executeAs(scope, () -> admit(request, device));
    if (refusal != null) {
      throw refusal;
    }
    return unitOfWork.executeAs(scope, () -> work.apply(device));
  }

  /**
   * Step 5b and step 7 in their own transaction, before the work: stores the nonce and consumes the
   * rate limit. Returns the refusal, or null when the request is admitted.
   */
  private DeviceAuthFailure admit(SignedRequest request, AuthenticatedDevice device) {
    if (!devices.insertNonce(device.deviceId(), request.nonce())) {
      DeviceAuthFailure replay =
          new DeviceAuthFailure(
              ErrorCode.NONCE_REPLAY, SecurityEventType.NONCE_REPLAY, Severity.HIGH, null);
      recordEvent(replay, device.deviceId(), device.aqueductId(), request.ipAddress(), null);
      return replay;
    }
    RateLimitDecision decision =
        rateLimiter.tryConsume(RATE_KEY_PREFIX + device.deviceId(), deviceLimit);
    if (!decision.allowed()) {
      DeviceAuthFailure limited =
          new DeviceAuthFailure(
              ErrorCode.RATE_LIMITED,
              SecurityEventType.RATE_LIMITED,
              Severity.LOW,
              DEVICE_LIMIT,
              Map.of(RETRY_AFTER, decision.retryAfterSeconds()));
      recordEvent(limited, device.deviceId(), device.aqueductId(), request.ipAddress(), null);
      return limited;
    }
    return null;
  }

  /**
   * Records the event of a refusal of the chain and, for the ten-th invalid signature in the
   * window, the repeated event. It runs inside a transaction that commits.
   */
  private void recordRefusal(DeviceAuthContext context, DeviceAuthFailure failure) {
    if (failure.eventType() == null) {
      return;
    }
    DeviceCredentials device = context.credentials();
    UUID deviceId = device == null ? null : device.deviceId();
    UUID aqueductId = device == null ? null : device.aqueductId();
    recordEvent(failure, deviceId, aqueductId, context.request().ipAddress(), context.now());
  }

  /**
   * Stores an event, and the repeated-failure event when this invalid signature is the tenth of the
   * window. The caller decides the transaction.
   *
   * @param now the moment of the check, or null when the event happens inside the transaction
   */
  private void recordEvent(
      DeviceAuthFailure failure, UUID deviceId, UUID aqueductId, String address, Instant now) {
    String ipHmac = address == null ? null : hasher.hmac(address);
    Map<String, Object> details =
        failure.reason() == null ? Map.of() : Map.of("reason", failure.reason());
    events.record(
        new SecurityEvent(
            failure.eventType(), failure.severity(), null, aqueductId, ipHmac, details, deviceId));
    if (deviceId == null
        || failure.eventType() != SecurityEventType.INVALID_SIGNATURE
        || now == null) {
      return;
    }
    Instant since = now.minusSeconds(FieldLimits.DEVICE_REPEATED_FAILURES_WINDOW_SECONDS);
    if (devices.countSecurityEvents(deviceId, SecurityEventType.INVALID_SIGNATURE, since)
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
  }

  private static DeviceAuthHandler chain() {
    DeviceAuthHandler first = new ActiveDeviceHandler();
    first
        .linkWith(new ValidKeyHandler())
        .linkWith(new TimestampWindowHandler())
        .linkWith(new NonceUnseenHandler())
        .linkWith(new SignatureHandler());
    return first;
  }

  /** What the first read of a request finds: the device, if any, and whether its nonce is known. */
  private record Lookup(Optional<DeviceCredentials> device, boolean nonceSeen) {}
}
