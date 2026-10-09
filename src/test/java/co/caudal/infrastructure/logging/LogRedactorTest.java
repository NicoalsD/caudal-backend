package co.caudal.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactorTest {

  @Test
  void redactsAuthorizationHeader() {
    String line = LogRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.e30.abc");

    assertThat(line).doesNotContain("eyJhbGciOiJIUzI1NiJ9").contains(LogRedactor.MASK);
  }

  @Test
  void redactsBearerTokenInsideFreeText() {
    String line = LogRedactor.redact("calling ia with bearer abc.def-123 now");

    assertThat(line).isEqualTo("calling ia with Bearer [REDACTED] now");
  }

  @Test
  void redactsCookieHeader() {
    String line = LogRedactor.redact("Cookie: caudal_rt=opaque-refresh-value; theme=light");

    assertThat(line).doesNotContain("opaque-refresh-value").contains(LogRedactor.MASK);
  }

  @Test
  void redactsPasswordTokenAndSecretPairs() {
    String line =
        LogRedactor.redact("password=hunter2hunter2 refresh_token=r-1 client_secret: s3cr3t");

    assertThat(line)
        .doesNotContain("hunter2hunter2", "r-1", "s3cr3t")
        .contains("password=[REDACTED]", "refresh_token=[REDACTED]", "client_secret: [REDACTED]");
  }

  @Test
  void redactsJsonFields() {
    String line = LogRedactor.redact("{\"username\":\"test.operator\",\"password\":\"x y z\"}");

    assertThat(line).isEqualTo("{\"username\":\"test.operator\",\"password\":\"[REDACTED]\"}");
  }

  @Test
  void keepsTextWithoutCredentials() {
    String line = LogRedactor.redact("reading accepted for tank 0190f3a2");

    assertThat(line).isEqualTo("reading accepted for tank 0190f3a2");
  }

  @Test
  void acceptsNullAndEmpty() {
    assertThat(LogRedactor.redact(null)).isNull();
    assertThat(LogRedactor.redact("")).isEmpty();
  }
}
