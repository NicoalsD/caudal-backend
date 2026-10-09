package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.user.NormalizedPassword;
import org.junit.jupiter.api.Test;

class Argon2PasswordHasherAdapterTest {

  private static final Argon2Properties LOW_COST = new Argon2Properties(64, 1, 1, 5);
  private static final Argon2Properties HIGHER_COST = new Argon2Properties(128, 2, 1, 5);
  private static final NormalizedPassword PASSWORD = NormalizedPassword.of("clave-de-prueba-larga");

  private final Argon2PasswordHasherAdapter hasher =
      new Argon2PasswordHasherAdapter(Argon2PasswordHasherAdapter.encoderFor(LOW_COST));

  @Test
  void producesAnArgon2idHashInPhcFormat() {
    String hash = hasher.hash(PASSWORD);

    assertThat(hash).startsWith("$argon2id$v=19$m=64,t=1,p=1$");
    assertThat(hash).doesNotContain(PASSWORD.value());
  }

  @Test
  void usesAFreshSaltForEachHash() {
    assertThat(hasher.hash(PASSWORD)).isNotEqualTo(hasher.hash(PASSWORD));
  }

  @Test
  void matchesTheRightPasswordOnly() {
    String hash = hasher.hash(PASSWORD);

    assertThat(hasher.matches(PASSWORD, hash)).isTrue();
    assertThat(hasher.matches(NormalizedPassword.of("otra-clave-distinta"), hash)).isFalse();
  }

  @Test
  void treatsFullWidthAndAsciiFormsAsTheSamePassword() {
    String hash = hasher.hash(NormalizedPassword.of("Ｐａｓｓｗｏｒｄ１２３４"));

    assertThat(hasher.matches(NormalizedPassword.of("Password1234"), hash)).isTrue();
  }

  @Test
  void unreadableHashesNeverMatch() {
    assertThat(hasher.matches(PASSWORD, null)).isFalse();
    assertThat(hasher.matches(PASSWORD, "not-a-hash")).isFalse();
    assertThat(hasher.matches(PASSWORD, "$argon2id$garbage")).isFalse();
  }

  @Test
  void rejectsAPasswordAboveTheInputLimitWithoutHashing() {
    NormalizedPassword huge = NormalizedPassword.of("x".repeat(10_000));

    assertThat(hasher.matches(huge, hasher.hash(PASSWORD))).isFalse();
  }

  @Test
  void asksForRehashWhenTheParametersGotStronger() {
    String oldHash = hasher.hash(PASSWORD);
    Argon2PasswordHasherAdapter stronger =
        new Argon2PasswordHasherAdapter(Argon2PasswordHasherAdapter.encoderFor(HIGHER_COST));

    assertThat(stronger.needsRehash(oldHash)).isTrue();
    assertThat(hasher.needsRehash(oldHash)).isFalse();
    assertThat(stronger.needsRehash(stronger.hash(PASSWORD))).isFalse();
  }

  @Test
  void asksForRehashWhenTheHashCannotBeRead() {
    assertThat(hasher.needsRehash("legacy-hash")).isTrue();
  }
}
