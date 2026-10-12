package co.caudal.domain.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

  /** Secret of the SHA-1 test vectors of RFC 6238, appendix B. */
  private static final TotpSecret RFC =
      TotpSecret.of("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

  @Test
  void matchesTheTestVectorsOfRfc6238WithSixDigits() {
    assertThat(Totp.codeAt(RFC, Totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
    assertThat(Totp.codeAt(RFC, Totp.stepAt(Instant.ofEpochSecond(1_111_111_109))))
        .isEqualTo("081804");
    assertThat(Totp.codeAt(RFC, Totp.stepAt(Instant.ofEpochSecond(1_234_567_890))))
        .isEqualTo("005924");
    assertThat(Totp.codeAt(RFC, Totp.stepAt(Instant.ofEpochSecond(20_000_000_000L))))
        .isEqualTo("353130");
  }

  @Test
  void acceptsTheCurrentStepAndOneStepOfDrift() {
    Instant now = Instant.ofEpochSecond(1_234_567_890);
    long step = Totp.stepAt(now);

    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step), now, null)).hasValue(step);
    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step - 1), now, null)).hasValue(step - 1);
    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step + 1), now, null)).hasValue(step + 1);
    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step - 2), now, null)).isEmpty();
  }

  @Test
  void aCodeNeverWorksTwice() {
    Instant now = Instant.ofEpochSecond(1_234_567_890);
    long step = Totp.stepAt(now);

    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step), now, step)).isEmpty();
    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step - 1), now, step - 1)).isEmpty();
    assertThat(Totp.verify(RFC, Totp.codeAt(RFC, step), now, step - 1)).hasValue(step);
  }

  @Test
  void rejectsMalformedCodes() {
    Instant now = Instant.ofEpochSecond(59);

    assertThat(Totp.verify(RFC, null, now, null)).isEmpty();
    assertThat(Totp.verify(RFC, "28708", now, null)).isEmpty();
    assertThat(Totp.verify(RFC, "2870821", now, null)).isEmpty();
    assertThat(Totp.verify(RFC, "28708a", now, null)).isEmpty();
    assertThat(Totp.verify(RFC, "000000", now, null)).isEmpty();
  }

  @Test
  void aSecretRoundTripsThroughBase32AndNeverPrintsItself() {
    TotpSecret secret = TotpSecret.generate();

    assertThat(secret.bytes()).hasSize(20);
    assertThat(TotpSecret.of(Base32.decode(secret.base32()))).isEqualTo(secret);
    assertThat(secret.toString()).doesNotContain(secret.base32());
    assertThat(RFC.base32()).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    assertThat(secret).isNotEqualTo(TotpSecret.generate()).hasSameHashCodeAs(secret);
    assertThatThrownBy(() -> TotpSecret.of(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Base32.decode("not base32!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void buildsTheOtpauthUriForTheQrCode() {
    assertThat(RFC.otpauthUri("CAUDAL Demo", "junta.admin"))
        .isEqualTo(
            "otpauth://totp/CAUDAL%20Demo:junta.admin?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                + "&issuer=CAUDAL%20Demo&algorithm=SHA1&digits=6&period=30");
  }
}
