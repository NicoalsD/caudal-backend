package co.caudal.application.device.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Step 4 of the signature check: a nonce already seen is refused and nothing after it runs. */
class NonceUnseenHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private static final UUID DEVICE = UUID.fromString("0190f3a2-9999-7000-8000-000000000001");
  private static final String NONCE = "3f9a1c4e7b2d408f9e6a5c1b2d3e4f50";

  private static DeviceAuthContext context(boolean nonceSeen) {
    SignedRequest request =
        SignedRequest.of(
            "POST",
            "/api/v1/devices/telemetry",
            new SignedHeaders(
                List.of(DEVICE.toString()),
                List.of(Long.toString(NOW.getEpochSecond())),
                List.of(NONCE),
                List.of("A".repeat(86))),
            "{}".getBytes(StandardCharsets.UTF_8),
            null);
    DeviceCredentials device =
        new DeviceCredentials(
            DEVICE,
            UUID.randomUUID(),
            "ACTIVE",
            "GATEWAY",
            null,
            null,
            List.of(new DeviceKeyCredential(UUID.randomUUID(), "x".repeat(43))));
    return new DeviceAuthContext(request, NOW, device, nonceSeen);
  }

  @Test
  void aNewNonceGoesOn() throws Exception {
    assertThatCode(() -> new NonceUnseenHandler().handle(context(false)))
        .doesNotThrowAnyException();
  }

  @Test
  void aRepeatedNonceIsAHighSeverityReplay() throws Exception {
    DeviceAuthFailure failure =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            DeviceAuthFailure.class, () -> new NonceUnseenHandler().handle(context(true)));

    assertThat(failure.code()).isEqualTo(ErrorCode.NONCE_REPLAY);
    assertThat(failure.eventType()).isEqualTo(SecurityEventType.NONCE_REPLAY);
    assertThat(failure.severity()).isEqualTo(Severity.HIGH);
    assertThat(failure.reason()).isNull();
  }

  @Test
  void aRepeatedNonceStopsTheSignatureStep() throws Exception {
    AtomicBoolean signatureRan = new AtomicBoolean(false);
    DeviceAuthHandler probe =
        new DeviceAuthHandler() {
          @Override
          protected void check(DeviceAuthContext context) {
            signatureRan.set(true);
          }
        };
    DeviceAuthHandler first = new NonceUnseenHandler();
    first.linkWith(probe);

    org.assertj.core.api.Assertions.catchThrowableOfType(
        DeviceAuthFailure.class, () -> first.handle(context(true)));

    assertThat(signatureRan).isFalse();
  }
}
