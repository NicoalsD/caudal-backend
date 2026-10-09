package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

  @Test
  void parseTrimsAndLowersAsciiLetters() {
    assertThat(Username.parse("  Test.Operator  ").value()).isEqualTo("test.operator");
  }

  @Test
  void acceptsTheLengthLimits() {
    assertThat(Username.parse("a".repeat(FieldLimits.USERNAME_MIN)).value()).hasSize(3);
    assertThat(Username.parse("a".repeat(FieldLimits.USERNAME_MAX)).value()).hasSize(32);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ab",
        "",
        "   ",
        ".abc",
        "abc.",
        "-abc",
        "with space",
        "ñandu",
        "junta@example.test",
        "a‮b‮c",
        "abc​",
        "ａｂｃｄ"
      })
  void rejectsTextThatIsNotAUsername(String raw) {
    assertThat(Username.tryParse(raw)).isEmpty();
    assertThatThrownBy(() -> Username.parse(raw)).isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsAUsernameAboveTheMaximum() {
    String tooLong = "a".repeat(FieldLimits.USERNAME_MAX + 1);

    assertThat(Username.tryParse(tooLong)).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  void tryParseReturnsEmptyForNull(String raw) {
    assertThat(Username.tryParse(raw)).isEmpty();
  }

  @Test
  void doesNotMapNonAsciiUppercaseIntoAnotherAccount() {
    // Turkish dotted capital I would become a plain "i" with locale based lower casing.
    assertThat(Username.tryParse("İsabel")).isEmpty();
  }

  @Test
  void errorIsAGenericFormatErrorWithoutTheValue() {
    assertThatThrownBy(() -> Username.parse("Bad Name!"))
        .isInstanceOfSatisfying(
            InvalidUsernameException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(ex.details().toString()).contains("username").doesNotContain("Bad");
            });
  }

  @Test
  void toStringIsTheCanonicalValue() {
    assertThat(Username.parse("Junta-2")).hasToString("junta-2");
  }
}
