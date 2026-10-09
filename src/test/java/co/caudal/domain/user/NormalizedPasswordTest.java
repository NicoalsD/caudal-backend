package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class NormalizedPasswordTest {

  @Test
  void fullWidthFormEqualsTheAsciiForm() {
    assertThat(NormalizedPassword.of("Ｐａｓｓｗｏｒｄ１２３４"))
        .isEqualTo(NormalizedPassword.of("Password1234"));
  }

  @Test
  void composedAndDecomposedAccentsAreTheSamePassword() {
    String composed = "contraseña-larga";
    String decomposed = "contraseña-larga";

    assertThat(NormalizedPassword.of(decomposed)).isEqualTo(NormalizedPassword.of(composed));
  }

  @Test
  void compatibilityLigaturesAreExpanded() {
    assertThat(NormalizedPassword.of("ﬁnal").value()).isEqualTo("final");
  }

  @Test
  void neverTrimsSpaces() {
    assertThat(NormalizedPassword.of("  secreto largo  ").value()).isEqualTo("  secreto largo  ");
  }

  @Test
  void lengthCountsCodePointsNotUtf16Units() {
    assertThat(NormalizedPassword.of("a😀b").length()).isEqualTo(3);
    assertThat(NormalizedPassword.of("Ｐａｓｓ").length()).isEqualTo(4);
  }

  @Test
  void toStringDoesNotLeakThePassword() {
    NormalizedPassword password = NormalizedPassword.of("super-secret-value");

    assertThat(password.toString()).isEqualTo("[REDACTED]").doesNotContain("secret");
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> NormalizedPassword.of(null));
  }
}
