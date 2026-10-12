package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.FieldLimits;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ResetCodeTest {

  @Test
  void aGeneratedCodeHasTheCanonicalFormat() {
    ResetCode code = ResetCode.generate();

    assertThat(code.value()).matches(FieldLimits.RESET_CODE_PATTERN);
    assertThat(code.value()).hasSize(FieldLimits.RESET_CODE_LENGTH);
    assertThat(code.display()).matches("^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$");
  }

  @Test
  void generatedCodesDoNotRepeat() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      codes.add(ResetCode.generate().value());
    }

    assertThat(codes).hasSize(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ABCD-EFGH-JKMN",
        "abcd-efgh-jkmn",
        "  abcd efgh jkmn ",
        "ABCDEFGHJKMN",
        "A B C D - E F G H - J K M N"
      })
  void readsWhatAPersonTypes(String typed) {
    assertThat(ResetCode.parse(typed)).contains(new ResetCode("ABCDEFGHJKMN"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "ABCD-EFGH",
        "ABCD-EFGH-JKMN-PQRS",
        "ABCD-EFGH-JKMI",
        "ABCD-EFGH-JKMO",
        "ABCD-EFGH-JKM!",
        "ñBCD-EFGH-JKMN"
      })
  void refusesWhatCannotBeACode(String typed) {
    assertThat(ResetCode.parse(typed)).isEmpty();
  }

  @Test
  void refusesAnAbsurdlyLongTextWithoutLookingAtIt() {
    assertThat(ResetCode.parse("A".repeat(FieldLimits.RESET_CODE_INPUT_MAX + 1))).isEmpty();
  }

  @Test
  void theHashIsTheSha256OfTheCanonicalValue() {
    ResetCode code = new ResetCode("ABCDEFGHJKMN");

    assertThat(code.hash()).hasSize(FieldLimits.SHA256_HEX_LENGTH).matches("^[0-9a-f]{64}$");
    assertThat(ResetCode.parse("abcd-efgh-jkmn").orElseThrow().hash()).isEqualTo(code.hash());
    assertThat(code.hash()).isNotEqualTo(new ResetCode("ABCDEFGHJKMP").hash());
  }

  @Test
  void theConstructorRejectsANonCanonicalValue() {
    assertThatThrownBy(() -> new ResetCode("abcdefghjkmn"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void neverPrintsTheCode() {
    assertThat(new ResetCode("ABCDEFGHJKMN").toString()).doesNotContain("ABCD");
  }
}
