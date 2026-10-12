package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnusablePasswordTest {

  @Test
  void isRandomAndLongEnoughToBeAValidPassword() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      NormalizedPassword password = UnusablePassword.generate();
      assertThat(password.length()).isEqualTo(43);
      assertThat(password.value()).matches("^[A-Za-z0-9_-]{43}$");
      seen.add(password.value());
    }

    assertThat(seen).hasSize(100);
  }

  @Test
  void neverPrintsItsValue() {
    assertThat(UnusablePassword.generate().toString()).isEqualTo("[REDACTED]");
  }
}
