package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.user.NormalizedPassword;
import org.junit.jupiter.api.Test;

/** Checks the canonical production parameters once (docs/Seguridad.md, section 2.1). */
class Argon2ParametersTest {

  private static final int CANONICAL_MEMORY_KIB = 19_456;
  private static final int CANONICAL_ITERATIONS = 2;
  private static final int CANONICAL_PARALLELISM = 1;

  @Test
  void productionParametersProduceM19456T2P1Hash() {
    var hasher =
        new Argon2PasswordHasherAdapter(
            Argon2PasswordHasherAdapter.encoderFor(
                new Argon2Properties(
                    CANONICAL_MEMORY_KIB, CANONICAL_ITERATIONS, CANONICAL_PARALLELISM, 5)));
    NormalizedPassword password = NormalizedPassword.of("clave-de-prueba-larga");

    String hash = hasher.hash(password);

    assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    assertThat(hasher.matches(password, hash)).isTrue();
    assertThat(hasher.needsRehash(hash)).isFalse();
  }
}
