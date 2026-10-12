package co.caudal.domain.device;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.shared.hash.Sha256;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CanonicalRequestTest {

  private static final String EMPTY_BODY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private static final String NONCE = "3f9a1c4e7b2d408f9e6a5c1b2d3e4f50";

  @Test
  void joinsTheFiveLinesWithLfAndNoFinalLf() {
    byte[] body = "{\"points\":[]}".getBytes(StandardCharsets.UTF_8);

    String text =
        new String(
            CanonicalRequest.of("POST", "/api/v1/devices/telemetry", "1791538200", NONCE, body),
            StandardCharsets.UTF_8);

    assertThat(text)
        .isEqualTo("POST\n/api/v1/devices/telemetry\n1791538200\n" + NONCE + "\n" + sha(body));
    assertThat(text).doesNotEndWith("\n").doesNotContain("\r");
    assertThat(text.lines().count()).isEqualTo(5);
  }

  @Test
  void anEmptyBodyHashesTheEmptyString() {
    String text =
        new String(
            CanonicalRequest.of(
                "GET", "/api/v1/devices/commands", "1791538200", NONCE, new byte[0]),
            StandardCharsets.UTF_8);

    assertThat(text.lines().toList().get(4)).isEqualTo(EMPTY_BODY_SHA256);
  }

  @Test
  void theQueryStringGoesInThePathLine() {
    String text =
        new String(
            CanonicalRequest.of(
                "GET", "/api/v1/devices/commands?x=1", "1791538200", NONCE, new byte[0]),
            StandardCharsets.UTF_8);

    assertThat(text.lines().toList().get(1)).isEqualTo("/api/v1/devices/commands?x=1");
  }

  @Test
  void aSingleByteChangeInTheBodyChangesTheHash() {
    byte[] original = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    byte[] altered = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);

    assertThat(sha(altered)).isNotEqualTo(sha(original));
  }

  private static String sha(byte[] body) {
    return Sha256.hex(body);
  }
}
