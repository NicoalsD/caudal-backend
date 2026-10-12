package co.caudal.application.device.telemetry;

import co.caudal.application.device.auth.AuthenticatedDevice;
import co.caudal.application.device.auth.DeviceAuthenticator;
import co.caudal.application.device.auth.SignedRequest;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.TelemetryPayloadPort;
import co.caudal.application.port.out.TelemetryPort;
import co.caudal.application.reading.ReadingInput;
import co.caudal.application.reading.ReadingOrigin;
import co.caudal.application.reading.ReadingRecorder;
import co.caudal.application.reading.ReadingRecorder.Verdict;
import co.caudal.application.reading.RecordedReading;
import co.caudal.application.rules.RuleSetView;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Receives a batch of telemetry of a device (docs/Protocolo-de-dispositivos.md, sections 4.1 to
 * 4.3). The signature is checked first, then the body is read, and every point is judged on its
 * own: a refused point does not stop the batch.
 *
 * <p>A level point becomes a reading of the tank of the sensor, with the same validation chain as a
 * manual reading. The point is stored after its reading, so it can point to it. A point whose id is
 * already stored with the same content is a duplicate; with other content it is a conflict.
 */
public final class DeviceTelemetryService {

  /** Metric whose points become readings. */
  static final String LEVEL = "LEVEL";

  /** Water appearance of the readings of a sensor, which cannot see the water. */
  static final String NOT_OBSERVED = "NOT_OBSERVED";

  private static final String CONFLICT = "CONFLICT";

  private final DeviceAuthenticator authenticator;
  private final TelemetryPayloadPort payloads;
  private final TelemetryPort telemetry;
  private final DevicePort devices;
  private final ReadingRecorder recorder;
  private final RuleSetPort ruleSets;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param authenticator checks the signature and runs the work in its transaction
   * @param payloads reads the body
   * @param telemetry the stored points
   * @param devices the devices, for the last contact
   * @param recorder judges and stores the readings
   * @param ruleSets the active rules
   * @param clock source of time
   */
  public DeviceTelemetryService(
      DeviceAuthenticator authenticator,
      TelemetryPayloadPort payloads,
      TelemetryPort telemetry,
      DevicePort devices,
      ReadingRecorder recorder,
      RuleSetPort ruleSets,
      Clock clock) {
    this.authenticator = authenticator;
    this.payloads = payloads;
    this.telemetry = telemetry;
    this.devices = devices;
    this.recorder = recorder;
    this.ruleSets = ruleSets;
    this.clock = clock;
  }

  /**
   * Verifies a signed batch and stores its points.
   *
   * @param request the signed request, with its raw body
   * @return one result per point
   * @throws co.caudal.shared.error.DomainException for a request that fails the signature, the rate
   *     limit or the shape of the body
   */
  public TelemetryBatchResult receive(SignedRequest request) {
    return authenticator.authenticate(
        request, device -> process(device, payloads.parse(request.body())));
  }

  private TelemetryBatchResult process(AuthenticatedDevice device, List<TelemetryPoint> points) {
    RuleSetView rules = null;
    if (points.stream().anyMatch(point -> LEVEL.equals(point.metric()))) {
      rules = ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
    List<TelemetryPointResult> results = new ArrayList<>(points.size());
    for (TelemetryPoint point : points) {
      results.add(handle(device, point, rules));
    }
    devices.touchLastSeen(device.deviceId(), clock.instant());
    return new TelemetryBatchResult(results);
  }

  private TelemetryPointResult handle(
      AuthenticatedDevice device, TelemetryPoint point, RuleSetView rules) {
    try {
      Optional<StoredTelemetryPoint> stored = telemetry.find(point.id());
      if (stored.isPresent()) {
        return sameAs(stored.get(), device, point)
            ? TelemetryPointResult.duplicate(point.id(), stored.get().readingId())
            : TelemetryPointResult.rejected(point.id(), CONFLICT);
      }
      if (LEVEL.equals(point.metric())) {
        return level(device, point, rules);
      }
      return other(device, point);
    } catch (DomainException refused) {
      return TelemetryPointResult.rejected(point.id(), codeOf(refused));
    }
  }

  /** A point that is not a level: stored as it is, without a reading. */
  private TelemetryPointResult other(AuthenticatedDevice device, TelemetryPoint point) {
    if (!telemetry.insert(device.aqueductId(), device.deviceId(), point, null)) {
      return TelemetryPointResult.rejected(point.id(), CONFLICT);
    }
    return TelemetryPointResult.accepted(point.id(), null, null);
  }

  /** A level point: judged and stored as a reading, then stored as a point that points to it. */
  private TelemetryPointResult level(
      AuthenticatedDevice device, TelemetryPoint point, RuleSetView rules) {
    if (point.value().stripTrailingZeros().scale() > FieldLimits.GAUGE_DECIMALS_MAX) {
      return TelemetryPointResult.rejected(point.id(), "VALIDATION_ERROR");
    }
    if (device.tankId() == null) {
      return TelemetryPointResult.rejected(point.id(), "VALIDATION_ERROR");
    }
    ReadingInput input =
        new ReadingInput(
            point.id(),
            device.tankId(),
            point.value(),
            NOT_OBSERVED,
            false,
            null,
            point.observedAt());
    Verdict verdict = recorder.judgeFor(device.aqueductId(), input, rules, false);
    RecordedReading recorded =
        recorder.storeFor(
            device.aqueductId(), null, verdict, rules, ReadingOrigin.sensor(device.deviceId()));
    UUID readingId = recorded.reading().id();
    if (!telemetry.insert(device.aqueductId(), device.deviceId(), point, readingId)) {
      return TelemetryPointResult.rejected(point.id(), CONFLICT);
    }
    return TelemetryPointResult.accepted(
        point.id(), readingId, recorded.reading().validationStatus());
  }

  private static boolean sameAs(
      StoredTelemetryPoint stored, AuthenticatedDevice device, TelemetryPoint point) {
    return stored.deviceId().equals(device.deviceId())
        && stored.metric().equals(point.metric())
        && stored.value().compareTo(point.value()) == 0
        && stored.observedAt().equals(point.observedAt());
  }

  /** The code of a refusal; a reading whose id was taken meanwhile is a conflict of the point. */
  private static String codeOf(DomainException refused) {
    return refused.code() == ErrorCode.DUPLICATE_READING ? CONFLICT : refused.code().name();
  }
}
