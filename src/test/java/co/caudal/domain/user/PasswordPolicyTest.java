package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.user.PasswordPolicyViolationException.Rule;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private static final Username USER = Username.parse("test.operator");
  private final PasswordPolicy policy =
      new PasswordPolicy(Set.of("Password1234", "contraseña-común"));

  private Rule ruleBrokenBy(String raw) {
    try {
      policy.check(NormalizedPassword.of(raw), USER);
    } catch (PasswordPolicyViolationException ex) {
      return ex.rule();
    }
    throw new AssertionError("The password should have been rejected");
  }

  @Test
  void elevenCharactersFail() {
    assertThat(ruleBrokenBy("a".repeat(FieldLimits.PASSWORD_MIN - 1))).isEqualTo(Rule.TOO_SHORT);
  }

  @Test
  void twelveCharactersPass() {
    assertThatCode(() -> policy.check(NormalizedPassword.of("tres-ranas-azules"), USER))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                policy.check(
                    NormalizedPassword.of("x7".repeat(FieldLimits.PASSWORD_MIN / 2)), USER))
        .doesNotThrowAnyException();
  }

  @Test
  void a129CharacterPasswordFails() {
    assertThat(ruleBrokenBy("a1".repeat(FieldLimits.PASSWORD_MAX / 2) + "z"))
        .isEqualTo(Rule.TOO_LONG);
  }

  @Test
  void the128CharacterPasswordPasses() {
    assertThatCode(
            () ->
                policy.check(
                    NormalizedPassword.of("a1".repeat(FieldLimits.PASSWORD_MAX / 2)), USER))
        .doesNotThrowAnyException();
  }

  @Test
  void fullWidthFormOfACommonPasswordIsStillCommon() {
    assertThat(ruleBrokenBy("Ｐａｓｓｗｏｒｄ１２３４")).isEqualTo(Rule.COMMON);
  }

  @Test
  void commonPasswordsAreCaseInsensitive() {
    assertThat(ruleBrokenBy("PASSWORD1234")).isEqualTo(Rule.COMMON);
    assertThat(ruleBrokenBy("CONTRASEÑA-COMÚN")).isEqualTo(Rule.COMMON);
  }

  @Test
  void rejectsAPasswordThatContainsTheUsername() {
    assertThat(ruleBrokenBy("mi-TEST.operator-2026")).isEqualTo(Rule.CONTAINS_USERNAME);
  }

  @Test
  void usernameInsideAFullWidthPasswordIsFoundAfterNormalization() {
    assertThat(ruleBrokenBy("ｔｅｓｔ.ｏｐｅｒａｔｏｒ-9999")).isEqualTo(Rule.CONTAINS_USERNAME);
  }

  @Test
  void noCompositionRulesAreImposed() {
    assertThatCode(() -> policy.check(NormalizedPassword.of("solo minusculas largas"), USER))
        .doesNotThrowAnyException();
  }

  @Test
  void violationDetailsNameTheRuleAndNeverThePassword() {
    assertThatThrownBy(() -> policy.check(NormalizedPassword.of("password1234"), USER))
        .isInstanceOfSatisfying(
            PasswordPolicyViolationException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
              assertThat(ex.details()).containsEntry("rule", "COMMON");
              assertThat(ex.details().toString()).doesNotContain("password1234");
            });
  }
}
