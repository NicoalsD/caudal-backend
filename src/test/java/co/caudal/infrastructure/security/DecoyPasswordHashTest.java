package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.user.NormalizedPassword;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DecoyPasswordHashTest {

  private static final Argon2Properties COST = new Argon2Properties(4_096, 2, 1, 5);
  private static final int SAMPLES = 7;
  private static final double TOLERANCE_FACTOR = 4.0;
  private static final NormalizedPassword TYPED =
      NormalizedPassword.of("lo-que-escribio-la-persona");

  private final PasswordEncoder encoder = Argon2PasswordHasherAdapter.encoderFor(COST);

  @Test
  void decoyUsesTheSameArgon2idParametersAsRealHashes() {
    DecoyPasswordHash decoy = new DecoyPasswordHash(encoder);

    assertThat(decoy.encodedHash()).startsWith("$argon2id$v=19$m=4096,t=2,p=1$");
    assertThat(encoder.upgradeEncoding(decoy.encodedHash())).isFalse();
  }

  @Test
  void nobodyCanAuthenticateAgainstTheDecoy() {
    DecoyPasswordHash decoy = new DecoyPasswordHash(encoder);

    assertThat(decoy.verify(TYPED)).isFalse();
    assertThat(decoy.verify(NormalizedPassword.of(decoy.encodedHash()))).isFalse();
  }

  @Test
  void eachInstanceHasItsOwnRandomHash() {
    assertThat(new DecoyPasswordHash(encoder).encodedHash())
        .isNotEqualTo(new DecoyPasswordHash(encoder).encodedHash());
  }

  /** Regression guard, not a precision measurement: same order of magnitude as a real check. */
  @Test
  void decoyVerificationTakesAboutAsLongAsARealOne() {
    Argon2PasswordHasherAdapter hasher = new Argon2PasswordHasherAdapter(encoder);
    String realHash = hasher.hash(TYPED);
    hasher.verifyAgainstDecoy(TYPED);
    hasher.matches(TYPED, realHash);

    long decoyNanos = medianNanos(() -> hasher.verifyAgainstDecoy(TYPED));
    long realNanos = medianNanos(() -> hasher.matches(TYPED, realHash));

    assertThat((double) decoyNanos / realNanos).isBetween(1 / TOLERANCE_FACTOR, TOLERANCE_FACTOR);
  }

  private static long medianNanos(Runnable action) {
    long[] samples = new long[SAMPLES];
    for (int i = 0; i < SAMPLES; i++) {
      long start = System.nanoTime();
      action.run();
      samples[i] = System.nanoTime() - start;
    }
    Arrays.sort(samples);
    return samples[SAMPLES / 2];
  }
}
