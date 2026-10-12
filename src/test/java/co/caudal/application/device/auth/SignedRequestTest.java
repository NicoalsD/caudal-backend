package co.caudal.application.device.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignedRequestTest {

  private static final String DEVICE = "0190f3a2-9999-7000-8000-000000000001";
  private static final String TIMESTAMP = "1791538200";
  private static final String NONCE = "3f9a1c4e7b2d408f9e6a5c1b2d3e4f50";
  private static final String SIGNATURE = "A".repeat(86);
  private static final String PATH = "/api/v1/devices/telemetry";
  private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);

  private static SignedHeaders headers(
      List<String> ids, List<String> timestamps, List<String> nonces, List<String> signatures) {
    return new SignedHeaders(ids, timestamps, nonces, signatures);
  }

  private static SignedRequest parse(SignedHeaders headers) {
    return SignedRequest.of("POST", PATH, headers, BODY, "203.0.113.7");
  }

  @Test
  void parsesEachHeaderSentOnce() {
    SignedRequest request =
        parse(headers(List.of(DEVICE), List.of(TIMESTAMP), List.of(NONCE), List.of(SIGNATURE)));

    assertThat(request.deviceId().toString()).isEqualTo(DEVICE);
    assertThat(request.timestamp()).isEqualTo(1791538200L);
    assertThat(request.timestampText()).isEqualTo(TIMESTAMP);
    assertThat(request.signature()).hasSize(64);
    assertThat(new String(request.canonical(), StandardCharsets.UTF_8))
        .isEqualTo(
            "POST\n"
                + PATH
                + "\n"
                + TIMESTAMP
                + "\n"
                + NONCE
                + "\n"
                + co.caudal.shared.hash.Sha256.hex(BODY));
  }

  @Test
  void aMissingHeaderIsAnInvalidSignature() {
    assertThatThrownBy(
            () -> parse(headers(List.of(), List.of(TIMESTAMP), List.of(NONCE), List.of(SIGNATURE))))
        .isInstanceOfSatisfying(
            DeviceAuthFailure.class,
            failure -> {
              assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
              assertThat(failure.eventType()).isNull();
            });
  }

  @Test
  void aRepeatedHeaderIsAnInvalidSignature() {
    assertThatThrownBy(
            () ->
                parse(
                    headers(
                        List.of(DEVICE),
                        List.of(TIMESTAMP, TIMESTAMP),
                        List.of(NONCE),
                        List.of(SIGNATURE))))
        .isInstanceOfSatisfying(
            DeviceAuthFailure.class,
            failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE));
  }

  @Test
  void aBadFormatIsAValidationError() {
    assertBadFormat(
        headers(List.of("no-es-uuid"), List.of(TIMESTAMP), List.of(NONCE), List.of(SIGNATURE)));
    assertBadFormat(
        headers(List.of(DEVICE), List.of("01791538200"), List.of(NONCE), List.of(SIGNATURE)));
    assertBadFormat(
        headers(
            List.of(DEVICE),
            List.of("1791538200"),
            List.of("ABCDEF" + NONCE.substring(6)),
            List.of(SIGNATURE)));
    assertBadFormat(
        headers(
            List.of(DEVICE), List.of(TIMESTAMP), List.of(NONCE.substring(1)), List.of(SIGNATURE)));
    assertBadFormat(
        headers(List.of(DEVICE), List.of(TIMESTAMP), List.of(NONCE), List.of("A".repeat(85))));
    assertBadFormat(
        headers(List.of(DEVICE), List.of(TIMESTAMP), List.of(NONCE), List.of("!".repeat(86))));
  }

  private static void assertBadFormat(SignedHeaders headers) {
    assertThatThrownBy(() -> parse(headers))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }
}
