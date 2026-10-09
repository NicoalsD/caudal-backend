package co.caudal.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.PhysicalConstants;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlywayPlaceholdersTest {

  private final Map<String, String> placeholders = FlywayPlaceholders.values();

  @Test
  void exposesFieldLimitsInLowerCase() {
    assertThat(placeholders)
        .containsEntry("person_name_max", String.valueOf(FieldLimits.PERSON_NAME_MAX))
        .containsEntry("username_min", String.valueOf(FieldLimits.USERNAME_MIN))
        .containsEntry("username_pattern", FieldLimits.USERNAME_PATTERN);
  }

  @Test
  void exposesPhysicalConstants() {
    assertThat(placeholders)
        .containsEntry("hours_per_day", String.valueOf(PhysicalConstants.HOURS_PER_DAY))
        .containsEntry("days_per_week", String.valueOf(PhysicalConstants.DAYS_PER_WEEK));
  }

  @Test
  void patternsDoNotBreakFlywayPlaceholderSyntax() {
    assertThat(placeholders.values()).noneMatch(value -> value.contains("${"));
  }
}
