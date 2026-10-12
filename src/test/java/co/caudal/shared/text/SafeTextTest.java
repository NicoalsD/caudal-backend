package co.caudal.shared.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.FieldValidationException;
import org.junit.jupiter.api.Test;

class SafeTextTest {

  @Test
  void normalizesLongTexts() {
    assertThat(SafeText.longText("d", "  Fuga\r\nen la calle  ", 3, 50))
        .isEqualTo("Fuga\nen la calle");
    assertThat(SafeText.longText("d", "Cafe" + Character.toString(0x0301) + " roto", 3, 50))
        .isEqualTo("Café roto");
  }

  @Test
  void refusesMissingShortLongAndForbiddenText() {
    assertThatThrownBy(() -> SafeText.longText("d", "   ", 3, 50))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> SafeText.longText("d", "ab", 3, 50))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> SafeText.longText("d", "a".repeat(51), 3, 50))
        .isInstanceOf(FieldValidationException.class);
    for (int forbidden : new int[] {0x0000, 0x202E, 0x200B, 0x2028, 0xFEFF, 0x0085}) {
      String bad = "a" + Character.toString(forbidden) + "b";
      assertThatThrownBy(() -> SafeText.longText("d", bad + "cdef", 3, 50))
          .isInstanceOf(FieldValidationException.class);
    }
  }

  @Test
  void oneLineTextsAreOptionalAndHaveNoBreaks() {
    assertThat(SafeText.optionalLine("l", null, 10)).isNull();
    assertThat(SafeText.optionalLine("l", "   ", 10)).isNull();
    assertThat(SafeText.optionalLine("l", " Esquina ", 10)).isEqualTo("Esquina");
    assertThatThrownBy(() -> SafeText.optionalLine("l", "a\nb", 10))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> SafeText.optionalLine("l", "a".repeat(11), 10))
        .isInstanceOf(FieldValidationException.class);
  }
}
