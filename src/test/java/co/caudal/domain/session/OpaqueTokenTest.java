package co.caudal.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpaqueTokenTest {

  @Test
  void carries256BitsOfEntropy() {
    OpaqueToken token = OpaqueToken.generate();

    assertThat(Base64.getUrlDecoder().decode(token.raw())).hasSize(32);
    assertThat(token.raw()).matches("^[A-Za-z0-9_-]{43}$");
  }

  @Test
  void tokensDoNotRepeat() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      assertThat(seen.add(OpaqueToken.generate().raw())).isTrue();
    }
  }

  @Test
  void hashIsTheLowerCaseHexSha256() {
    assertThat(OpaqueToken.hashOf("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void hashIsSixtyFourCharactersAndDiffersFromTheSecret() {
    OpaqueToken token = OpaqueToken.generate();

    assertThat(token.hash()).hasSize(64).matches("^[0-9a-f]{64}$").isNotEqualTo(token.raw());
    assertThat(token.hash()).isEqualTo(OpaqueToken.hashOf(token.raw()));
  }

  @Test
  void rejectsPresentedValuesThatCannotBeOurs() {
    assertThat(OpaqueToken.isPlausible(null)).isFalse();
    assertThat(OpaqueToken.isPlausible("")).isFalse();
    assertThat(OpaqueToken.isPlausible("x".repeat(OpaqueToken.MAX_PRESENTED_LENGTH + 1))).isFalse();
    assertThat(OpaqueToken.isPlausible(OpaqueToken.generate().raw())).isTrue();
  }

  @Test
  void toStringDoesNotShowTheSecret() {
    OpaqueToken token = OpaqueToken.generate();

    assertThat(token.toString()).doesNotContain(token.raw());
  }
}
