package co.caudal.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldLimitsTest {

  @Test
  void minimumsAreBelowMaximums() {
    assertThat(FieldLimits.USERNAME_MIN).isLessThan(FieldLimits.USERNAME_MAX);
    assertThat(FieldLimits.PASSWORD_MIN).isLessThan(FieldLimits.PASSWORD_MAX);
    assertThat(FieldLimits.PERSON_NAME_MIN).isLessThan(FieldLimits.PERSON_NAME_MAX);
    assertThat(FieldLimits.AQUEDUCT_NAME_MIN).isLessThan(FieldLimits.AQUEDUCT_NAME_MAX);
    assertThat(FieldLimits.AQUEDUCT_SLUG_MIN).isLessThan(FieldLimits.AQUEDUCT_SLUG_MAX);
    assertThat(FieldLimits.PLACE_NAME_MIN).isLessThan(FieldLimits.PLACE_NAME_MAX);
    assertThat(FieldLimits.REASON_MIN).isLessThan(FieldLimits.REASON_MAX);
    assertThat(FieldLimits.PAGE_LIMIT_DEFAULT)
        .isBetween(FieldLimits.PAGE_LIMIT_MIN, FieldLimits.PAGE_LIMIT_MAX);
  }

  @Test
  void textFieldsFitTheGlobalMaximum() {
    assertThat(FieldLimits.NOTE_MAX).isLessThanOrEqualTo(FieldLimits.TEXT_GLOBAL_MAX);
    assertThat(FieldLimits.INCIDENT_DESCRIPTION_MAX)
        .isLessThanOrEqualTo(FieldLimits.TEXT_GLOBAL_MAX);
    assertThat(FieldLimits.REASON_MAX).isLessThanOrEqualTo(FieldLimits.TEXT_GLOBAL_MAX);
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "test.operator", "junta-2", "a_b.c-d"})
  void acceptsValidUsernames(String username) {
    assertThat(username).matches(FieldLimits.USERNAME_PATTERN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab", "Test", ".abc", "abc.", "with space", "ñandu"})
  void rejectsInvalidUsernames(String username) {
    assertThat(username).doesNotMatch(FieldLimits.USERNAME_PATTERN);
  }

  @Test
  void usernamePatternMatchesTheLengthLimits() {
    assertThat("a".repeat(FieldLimits.USERNAME_MAX)).matches(FieldLimits.USERNAME_PATTERN);
    assertThat("a".repeat(FieldLimits.USERNAME_MAX + 1)).doesNotMatch(FieldLimits.USERNAME_PATTERN);
    assertThat("a".repeat(FieldLimits.USERNAME_MIN)).matches(FieldLimits.USERNAME_PATTERN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Persona de Prueba", "María José O'Neil", "Ana-Lucía", "Zoë"})
  void acceptsPersonNames(String name) {
    assertThat(name).matches(FieldLimits.PERSON_NAME_PATTERN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1Ana", "Ana<script>", "Ana@x"})
  void rejectsInvalidPersonNames(String name) {
    assertThat(name).doesNotMatch(FieldLimits.PERSON_NAME_PATTERN);
  }

  @Test
  void codesAndSlugsUseAsciiPatterns() {
    assertThat("prueba-vereda").matches(FieldLimits.AQUEDUCT_SLUG_PATTERN);
    assertThat("prueba--vereda").doesNotMatch(FieldLimits.AQUEDUCT_SLUG_PATTERN);
    assertThat("V-01").matches(FieldLimits.VALVE_CODE_PATTERN);
    assertThat("S1").matches(FieldLimits.SECTOR_CODE_PATTERN);
    assertThat("CLEAR_WATER").matches(FieldLimits.CATALOG_CODE_PATTERN);
    assertThat("READING_CREATE").matches(FieldLimits.PERMISSION_CODE_PATTERN);
    assertThat("7K3M9QXZ").matches(FieldLimits.TRACKING_CODE_PATTERN);
    assertThat("7K3M9QXI").doesNotMatch(FieldLimits.TRACKING_CODE_PATTERN);
  }

  @Test
  void everyPatternCompiles() throws IllegalAccessException {
    for (var field : FieldLimits.class.getFields()) {
      if (field.getName().endsWith("_PATTERN")) {
        assertThat(Pattern.compile((String) field.get(null))).isNotNull();
      }
    }
  }
}
