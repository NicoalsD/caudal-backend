package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.shared.FieldLimits;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The lengths and ASCII patterns of the database are the ones of {@link FieldLimits}. */
class FieldLimitsDatabaseDriftIT extends DatabaseIT {

  static List<Arguments> columns() {
    return List.of(
        Arguments.of("iam", "users", "username", FieldLimits.USERNAME_MAX),
        Arguments.of("iam", "users", "full_name", FieldLimits.PERSON_NAME_MAX),
        Arguments.of("iam", "permissions", "code", FieldLimits.PERMISSION_CODE_MAX),
        Arguments.of("iam", "security_events", "request_id", FieldLimits.REQUEST_ID_MAX),
        Arguments.of("org", "aqueducts", "slug", FieldLimits.AQUEDUCT_SLUG_MAX),
        Arguments.of("org", "aqueducts", "name", FieldLimits.AQUEDUCT_NAME_MAX),
        Arguments.of("org", "tanks", "name", FieldLimits.PLACE_NAME_MAX),
        Arguments.of("org", "sectors", "code", FieldLimits.SECTOR_CODE_MAX),
        Arguments.of("org", "sectors", "name", FieldLimits.PLACE_NAME_MAX),
        Arguments.of("org", "valves", "code", FieldLimits.VALVE_CODE_MAX),
        Arguments.of("org", "valves", "location_hint", FieldLimits.LOCATION_HINT_MAX),
        Arguments.of("org", "catalog_items", "code", FieldLimits.CATALOG_CODE_MAX),
        Arguments.of("org", "catalog_items", "label_es", FieldLimits.CATALOG_LABEL_MAX),
        Arguments.of("org", "rule_sets", "change_reason", FieldLimits.REASON_MAX),
        Arguments.of("ops", "readings", "note", FieldLimits.NOTE_MAX),
        Arguments.of("ops", "readings", "water_appearance_code", FieldLimits.CATALOG_CODE_MAX),
        Arguments.of("ops", "reading_corrections", "reason", FieldLimits.REASON_MAX),
        Arguments.of("ops", "incidents", "description", FieldLimits.INCIDENT_DESCRIPTION_MAX),
        Arguments.of("ops", "incidents", "category_code", FieldLimits.CATALOG_CODE_MAX),
        Arguments.of("ops", "day_closures", "notes", FieldLimits.NOTE_MAX),
        Arguments.of("devices", "devices", "name", FieldLimits.PLACE_NAME_MAX),
        Arguments.of("devices", "valve_commands", "manual_reason", FieldLimits.REASON_MAX),
        Arguments.of("audit", "audit_log", "request_id", FieldLimits.REQUEST_ID_MAX));
  }

  @ParameterizedTest(name = "{0}.{1}.{2}")
  @MethodSource("columns")
  void columnLengthMatchesFieldLimits(String schema, String table, String column, int max) {
    Integer length =
        owner.queryForObject(
            "SELECT character_maximum_length FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            Integer.class,
            schema,
            table,
            column);

    assertThat(length).isEqualTo(max);
  }

  @Test
  void asciiPatternsAreTheOnesOfFieldLimits() {
    assertThat(checkOf("users_username_format")).contains(FieldLimits.USERNAME_PATTERN);
    assertThat(checkOf("aqueducts_slug_format")).contains(FieldLimits.AQUEDUCT_SLUG_PATTERN);
    assertThat(checkOf("sectors_code_format")).contains(FieldLimits.SECTOR_CODE_PATTERN);
    assertThat(checkOf("valves_code_format")).contains(FieldLimits.VALVE_CODE_PATTERN);
    assertThat(checkOf("catalog_items_code_format")).contains(FieldLimits.CATALOG_CODE_PATTERN);
    assertThat(checkOf("permissions_code_format")).contains(FieldLimits.PERMISSION_CODE_PATTERN);
  }

  @Test
  void minimumLengthsAreTheOnesOfFieldLimits() {
    assertThat(checkOf("users_username_length")).contains(">= " + FieldLimits.USERNAME_MIN);
    assertThat(checkOf("users_full_name_length")).contains(">= " + FieldLimits.PERSON_NAME_MIN);
    assertThat(checkOf("incidents_description_length"))
        .contains(">= " + FieldLimits.INCIDENT_DESCRIPTION_MIN);
    assertThat(checkOf("reading_corrections_reason_length"))
        .contains(">= " + FieldLimits.REASON_MIN);
  }

  private String checkOf(String constraint) {
    return owner.queryForObject(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
        String.class,
        constraint);
  }
}
