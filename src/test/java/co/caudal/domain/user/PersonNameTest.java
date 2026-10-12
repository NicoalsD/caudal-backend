package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersonNameTest {

  @Test
  void normalizesToNfcTrimsAndCollapsesInnerSpaces() {
    String decomposed = Normalizer.normalize("José   María\tO'Neil ", Normalizer.Form.NFD);

    PersonName name = PersonName.parse("  " + decomposed);

    assertThat(name.value()).isEqualTo("José María O'Neil");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ana", "Ana-Lucía", "M. Pérez", "Zoë Müller", "Ñusta"})
  void acceptsLettersMarksDotApostropheAndHyphen(String text) {
    assertThat(PersonName.parse(text).value()).isEqualTo(text);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"1Ana", "Ana<script>", "Ana@x", "Ana\u0000", "Ana‮evil", "Ana​x", "Ana😀", "-Ana"})
  void rejectsForbiddenCharactersWithoutEchoingThem(String text) {
    assertThatThrownBy(() -> PersonName.parse(text))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(e.details().toString())
                  .contains("full_name", "FIELD_CHARACTERS_NOT_ALLOWED")
                  .doesNotContain("script");
            });
  }

  @Test
  void enforcesTheLengthLimitsInCodePoints() {
    assertThat(PersonName.parse("A".repeat(FieldLimits.PERSON_NAME_MIN)).value()).hasSize(2);
    assertThat(PersonName.parse("A".repeat(FieldLimits.PERSON_NAME_MAX)).value()).hasSize(80);

    assertThatThrownBy(() -> PersonName.parse("A"))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> assertThat(fieldCode(e)).isEqualTo("FIELD_TOO_SHORT"));
    assertThatThrownBy(() -> PersonName.parse(" "))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> assertThat(fieldCode(e)).isEqualTo("FIELD_TOO_SHORT"));
    assertThatThrownBy(() -> PersonName.parse("A".repeat(FieldLimits.PERSON_NAME_MAX + 1)))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> assertThat(fieldCode(e)).isEqualTo("FIELD_TOO_LONG"));
  }

  @Test
  void neverPrintsTheName() {
    assertThat(PersonName.parse("Ana Pérez").toString()).doesNotContain("Ana");
  }

  @SuppressWarnings("unchecked")
  private static String fieldCode(FieldValidationException e) {
    List<Map<String, String>> fields = (List<Map<String, String>>) e.details().get("fields");
    return fields.getFirst().get("code");
  }
}
