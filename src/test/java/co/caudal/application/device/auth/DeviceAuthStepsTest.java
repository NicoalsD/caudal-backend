package co.caudal.application.device.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.DeviceRequestSigner;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Each step of the signature check on its own, and the rule that a refusal stops the chain. */
class DeviceAuthStepsTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private static final String NONCE = "3f9a1c4e7b2d408f9e6a5c1b2d3e4f50";
  private static final String PATH = "/api/v1/devices/telemetry";
  private static final byte[] BODY = "{\"points\":[]}".getBytes(StandardCharsets.UTF_8);
  private static final UUID DEVICE = UUID.fromString("0190f3a2-9999-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-000000000002");

  private static DeviceAuthContext context(
      DeviceCredentials device, long timestamp, KeyPair signer, byte[] body) throws Exception {
    String text = Long.toString(timestamp);
    String signature = DeviceRequestSigner.sign(signer, "POST", PATH, text, NONCE, body);
    SignedRequest request =
        SignedRequest.of(
            "POST",
            PATH,
            DeviceRequestSigner.headers(DEVICE.toString(), text, NONCE, signature),
            body,
            null);
    return new DeviceAuthContext(request, NOW, device);
  }

  private static DeviceCredentials device(String status, List<DeviceKeyCredential> keys) {
    return new DeviceCredentials(DEVICE, AQUEDUCT, status, "GATEWAY", null, null, keys);
  }

  private static DeviceKeyCredential key(UUID id, KeyPair pair) {
    return new DeviceKeyCredential(id, DeviceRequestSigner.publicKeyText(pair));
  }

  private static DeviceAuthFailure refusedBy(DeviceAuthHandler step, DeviceAuthContext context) {
    try {
      step.handle(context);
    } catch (DeviceAuthFailure failure) {
      return failure;
    }
    throw new AssertionError("the step should have refused the request");
  }

  @Test
  void anUnknownDeviceGetsTheSameAnswerAsABadSignature() {
    DeviceAuthFailure failure =
        refusedBy(new ActiveDeviceHandler(), new DeviceAuthContext(requestOnly(), NOW, null));

    assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
    assertThat(failure.eventType()).isEqualTo(SecurityEventType.INVALID_SIGNATURE);
    assertThat(failure.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(failure.reason()).isEqualTo(ActiveDeviceHandler.BAD_SIGNATURE);
  }

  @Test
  void aSuspendedOrRetiredDeviceIsInactive() {
    for (String status : List.of("SUSPENDED", "RETIRED")) {
      DeviceAuthFailure failure =
          refusedBy(
              new ActiveDeviceHandler(),
              new DeviceAuthContext(requestOnly(), NOW, device(status, List.of())));

      assertThat(failure.code()).isEqualTo(ErrorCode.DEVICE_INACTIVE);
      assertThat(failure.eventType()).isEqualTo(SecurityEventType.PERMISSION_DENIED);
      assertThat(failure.reason()).isEqualTo("DEVICE_INACTIVE");
    }
  }

  @Test
  void anActiveDevicePassesTheFirstStep() {
    assertThatCode(
            () ->
                new ActiveDeviceHandler()
                    .handle(new DeviceAuthContext(requestOnly(), NOW, device("ACTIVE", List.of()))))
        .doesNotThrowAnyException();
  }

  @Test
  void aDeviceWithoutValidKeysIsRefused() {
    DeviceAuthFailure failure =
        refusedBy(
            new ValidKeyHandler(),
            new DeviceAuthContext(requestOnly(), NOW, device("ACTIVE", List.of())));

    assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
    assertThat(failure.reason()).isEqualTo("NO_ACTIVE_KEY");
  }

  @Test
  void theTimestampMustBeInsideTheWindowOnBothSides() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    DeviceCredentials device = device("ACTIVE", List.of(key(UUID.randomUUID(), pair)));
    long window = FieldLimits.DEVICE_SIGNATURE_WINDOW_SECONDS;

    assertThatCode(
            () ->
                new TimestampWindowHandler()
                    .handle(context(device, NOW.getEpochSecond() - window, pair, BODY)))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                new TimestampWindowHandler()
                    .handle(context(device, NOW.getEpochSecond() + window, pair, BODY)))
        .doesNotThrowAnyException();

    DeviceAuthFailure late =
        refusedBy(
            new TimestampWindowHandler(),
            context(device, NOW.getEpochSecond() - window - 1, pair, BODY));
    DeviceAuthFailure early =
        refusedBy(
            new TimestampWindowHandler(),
            context(device, NOW.getEpochSecond() + window + 1, pair, BODY));

    assertThat(late.code()).isEqualTo(ErrorCode.TIMESTAMP_OUT_OF_WINDOW);
    assertThat(late.severity()).isEqualTo(Severity.LOW);
    assertThat(late.eventType()).isEqualTo(SecurityEventType.INVALID_SIGNATURE);
    assertThat(early.code()).isEqualTo(ErrorCode.TIMESTAMP_OUT_OF_WINDOW);
  }

  @Test
  void aValidSignatureSetsTheKeyThatVerifiedIt() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    UUID keyId = UUID.randomUUID();
    DeviceAuthContext context =
        context(device("ACTIVE", List.of(key(keyId, pair))), NOW.getEpochSecond(), pair, BODY);

    new SignatureHandler().handle(context);

    assertThat(context.matchedKeyId()).isEqualTo(keyId);
  }

  @Test
  void duringARotationAnOlderValidKeyStillVerifies() throws Exception {
    KeyPair previous = DeviceRequestSigner.newKeyPair();
    KeyPair current = DeviceRequestSigner.newKeyPair();
    UUID previousId = UUID.randomUUID();
    DeviceCredentials device =
        device("ACTIVE", List.of(key(UUID.randomUUID(), current), key(previousId, previous)));
    DeviceAuthContext context = context(device, NOW.getEpochSecond(), previous, BODY);

    new SignatureHandler().handle(context);

    assertThat(context.matchedKeyId()).isEqualTo(previousId);
  }

  @Test
  void aSignatureOfAnotherKeyOrAnAlteredBodyIsInvalid() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    KeyPair stranger = DeviceRequestSigner.newKeyPair();
    DeviceCredentials device = device("ACTIVE", List.of(key(UUID.randomUUID(), pair)));

    DeviceAuthFailure wrongKey =
        refusedBy(new SignatureHandler(), context(device, NOW.getEpochSecond(), stranger, BODY));
    byte[] altered = BODY.clone();
    altered[altered.length - 1] ^= 1;
    DeviceAuthContext tampered =
        new DeviceAuthContext(
            SignedRequest.of(
                "POST",
                PATH,
                DeviceRequestSigner.headers(
                    DEVICE.toString(),
                    Long.toString(NOW.getEpochSecond()),
                    NONCE,
                    DeviceRequestSigner.sign(
                        pair, "POST", PATH, Long.toString(NOW.getEpochSecond()), NONCE, BODY)),
                altered,
                null),
            NOW,
            device);

    assertThat(wrongKey.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
    assertThat(wrongKey.reason()).isEqualTo("BAD_SIGNATURE");
    assertThat(refusedBy(new SignatureHandler(), tampered).code())
        .isEqualTo(ErrorCode.INVALID_SIGNATURE);
  }

  @Test
  void aRefusedRequestNeverReachesTheNextStep() throws Exception {
    AtomicBoolean reached = new AtomicBoolean(false);
    DeviceAuthHandler probe =
        new DeviceAuthHandler() {
          @Override
          protected void check(DeviceAuthContext context) {
            reached.set(true);
          }
        };
    DeviceAuthHandler first = new ActiveDeviceHandler();
    first.linkWith(probe);

    assertThatThrownBy(() -> first.handle(new DeviceAuthContext(requestOnly(), NOW, null)))
        .isInstanceOf(DeviceAuthFailure.class);
    assertThat(reached).isFalse();

    first.handle(new DeviceAuthContext(requestOnly(), NOW, device("ACTIVE", List.of())));
    assertThat(reached).isTrue();
  }

  @Test
  void theChainRefusesAtTheSignatureOnlyAfterTheEarlierSteps() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    DeviceAuthHandler first = new ActiveDeviceHandler();
    first
        .linkWith(new ValidKeyHandler())
        .linkWith(new TimestampWindowHandler())
        .linkWith(new SignatureHandler());
    DeviceAuthContext outOfWindow =
        context(
            device("ACTIVE", List.of(key(UUID.randomUUID(), pair))),
            NOW.getEpochSecond() - FieldLimits.DEVICE_SIGNATURE_WINDOW_SECONDS - 1,
            pair,
            BODY);

    DeviceAuthFailure failure = refusedBy(first, outOfWindow);

    assertThat(failure.code()).isEqualTo(ErrorCode.TIMESTAMP_OUT_OF_WINDOW);
    assertThat(outOfWindow.matchedKeyId()).isNull();
  }

  private static SignedRequest requestOnly() {
    return SignedRequest.of(
        "POST",
        PATH,
        DeviceRequestSigner.headers(
            DEVICE.toString(), Long.toString(NOW.getEpochSecond()), NONCE, "A".repeat(86)),
        BODY,
        null);
  }
}
