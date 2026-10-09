package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppendOnlyAndTriggersIT extends DatabaseIT {

  private static final int APPEND_ONLY_TABLES = 23;

  private UUID insertReading(UUID aqueduct, UUID ruleSet, String value) {
    return owner.queryForObject(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, source, rule_set_id, validation_status)"
            + " VALUES (uuidv7(), ?, ?, "
            + value
            + ", 'NORMAL', now(), 'MANUAL_APP', ?, 'ACCEPTED')"
            + " RETURNING id",
        UUID.class,
        aqueduct,
        tankOf(aqueduct),
        ruleSet);
  }

  @Test
  void everyAppendOnlyTableHasItsTriggers() {
    Integer tables =
        owner.queryForObject(
            "SELECT count(DISTINCT tgrelid) FROM pg_trigger WHERE tgname = 'append_only'",
            Integer.class);
    Integer truncate =
        owner.queryForObject(
            "SELECT count(DISTINCT tgrelid) FROM pg_trigger WHERE tgname = 'append_only_truncate'",
            Integer.class);

    assertThat(tables).isEqualTo(APPEND_ONLY_TABLES);
    assertThat(truncate).isEqualTo(APPEND_ONLY_TABLES);
  }

  @Test
  void readingsCannotBeUpdatedEvenByTheOwner() {
    UUID aqueduct = newAqueduct(false);
    UUID reading = insertReading(aqueduct, activeRuleSet(aqueduct), "2.10");

    assertThatThrownBy(
            () -> owner.update("UPDATE ops.readings SET gauge_value = 3 WHERE id = ?", reading))
        .hasStackTraceContaining("append_only_violation");
    assertThatThrownBy(() -> owner.execute("TRUNCATE ops.readings CASCADE"))
        .hasStackTraceContaining("append_only_violation");
  }

  @Test
  void theApplicationCannotUpdateNorDeleteReadings() throws SQLException {
    UUID aqueduct = newAqueduct(false);
    UUID reading = insertReading(aqueduct, activeRuleSet(aqueduct), "2.10");

    try (Connection app = appConnection(aqueduct);
        var statement = app.createStatement()) {
      assertThatThrownBy(
              () ->
                  statement.executeUpdate("DELETE FROM ops.readings WHERE id = '" + reading + "'"))
          .hasStackTraceContaining("permission denied");
    }
  }

  @Test
  void readingOutsideTheGaugeIsRejected() {
    UUID aqueduct = newAqueduct(false);
    UUID ruleSet = activeRuleSet(aqueduct);

    assertThatThrownBy(() -> insertReading(aqueduct, ruleSet, "8.00"))
        .hasStackTraceContaining("gauge_out_of_range");
  }

  @Test
  void correctionOutsideTheGaugeIsRejected() {
    UUID aqueduct = newAqueduct(false);
    UUID reading = insertReading(aqueduct, activeRuleSet(aqueduct), "2.10");

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO ops.reading_corrections (aqueduct_id, reading_id,"
                        + " corrected_gauge_value, reason, corrected_by)"
                        + " VALUES (?, ?, -1, 'Lectura mal tomada en campo.', ?)",
                    aqueduct,
                    reading,
                    systemUser()))
        .hasStackTraceContaining("gauge_out_of_range");
  }

  @Test
  void effectiveReadingUsesTheLatestCorrection() {
    UUID aqueduct = newAqueduct(false);
    UUID reading = insertReading(aqueduct, activeRuleSet(aqueduct), "2.10");
    owner.update(
        "INSERT INTO ops.reading_corrections (aqueduct_id, reading_id, corrected_gauge_value,"
            + " reason, corrected_by) VALUES (?, ?, 2.50, 'Se leyó mal la regla pintada.', ?)",
        aqueduct,
        reading,
        systemUser());

    String value =
        owner.queryForObject(
            "SELECT gauge_value::text FROM ops.effective_readings WHERE id = ?",
            String.class,
            reading);

    assertThat(value).isEqualTo("2.50");
  }

  @Test
  void activeRuleSetIsImmutable() {
    UUID aqueduct = newAqueduct(false);
    UUID ruleSet = activeRuleSet(aqueduct);

    assertThatThrownBy(
            () -> owner.update("UPDATE org.rule_sets SET reserve_level = 2 WHERE id = ?", ruleSet))
        .hasStackTraceContaining("rule_set_immutable");
    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO org.rule_level_bands (rule_set_id, band, min_level, max_level,"
                        + " daily_service_hours) VALUES (?, 'HIGH', 3.5, 5, 16)",
                    ruleSet))
        .hasStackTraceContaining("rule_set_immutable");
    owner.update(
        "UPDATE org.rule_sets SET status = 'SUPERSEDED', valid_to = now() WHERE id = ?", ruleSet);
    assertThatThrownBy(
            () -> owner.update("UPDATE org.rule_sets SET status = 'ACTIVE' WHERE id = ?", ruleSet))
        .hasStackTraceContaining("rule_set_immutable");
  }

  @Test
  void updatedAtFollowsEachUpdate() {
    UUID aqueduct = newAqueduct(false);
    owner.update(
        "UPDATE org.aqueducts SET updated_at = now() - interval '1 day' WHERE id = ?", aqueduct);
    owner.update("UPDATE org.aqueducts SET name = 'Vereda de Prueba Dos' WHERE id = ?", aqueduct);

    Boolean recent =
        owner.queryForObject(
            "SELECT updated_at > now() - interval '1 minute' FROM org.aqueducts WHERE id = ?",
            Boolean.class,
            aqueduct);

    assertThat(recent).isTrue();
  }

  @Test
  void importsOnlyIntoDemoAqueducts() {
    UUID real = newAqueduct(false);

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO sim.import_batches (aqueduct_id, kind, scenario_name, seed,"
                        + " simulator_version, rows_received, rows_accepted, rows_rejected,"
                        + " file_sha256, imported_by) VALUES (?, 'READINGS', 'prueba', 1, 'v1',"
                        + " 0, 0, 0, repeat('0', 64), ?)",
                    real,
                    systemUser()))
        .hasStackTraceContaining("demo_only");
  }

  @Test
  void finalMinutesNeverChange() {
    UUID aqueduct = newAqueduct(false);
    UUID minutes =
        owner.queryForObject(
            "INSERT INTO reporting.minutes (aqueduct_id, period_from, period_to, status, content,"
                + " generated_by) VALUES (?, current_date, current_date, 'FINAL', '{}', ?)"
                + " RETURNING id",
            UUID.class,
            aqueduct,
            systemUser());

    assertThatThrownBy(
            () ->
                owner.update(
                    "UPDATE reporting.minutes SET content = '{\"x\":1}' WHERE id = ?", minutes))
        .hasStackTraceContaining("minutes_final_immutable");
  }

  @Test
  void auditChainLinksRowsAndDetectsTampering() throws SQLException {
    UUID aqueduct = newAqueduct(false);
    for (int i = 0; i < 3; i++) {
      owner.update(
          "INSERT INTO audit.audit_log (aqueduct_id, actor_type, action, entity_type, entity_id)"
              + " VALUES (?, 'SYSTEM', 'TEST_EVENT', 'test', ?)",
          aqueduct,
          String.valueOf(i));
    }

    assertThat(owner.queryForObject("SELECT audit.verify_chain(?)", Long.class, aqueduct)).isNull();
    assertThat(
            owner.queryForObject(
                "SELECT prev_hash FROM audit.audit_log WHERE aqueduct_id = ? ORDER BY id LIMIT 1",
                String.class,
                aqueduct))
        .isEqualTo("0".repeat(64));

    owner.execute("ALTER TABLE audit.audit_log DISABLE TRIGGER append_only");
    try {
      owner.update(
          "UPDATE audit.audit_log SET entity_id = 'tampered' WHERE id ="
              + " (SELECT min(id) FROM audit.audit_log WHERE aqueduct_id = ?)",
          aqueduct);
    } finally {
      owner.execute("ALTER TABLE audit.audit_log ENABLE TRIGGER append_only");
    }
    assertThat(owner.queryForObject("SELECT audit.verify_chain(?)", Long.class, aqueduct))
        .isNotNull();
  }

  @Test
  void purgeRunsAsTheApplicationAndLogsFirst() throws SQLException {
    owner.update(
        "INSERT INTO iam.login_attempts (username_hmac, ip_hmac, succeeded, created_at)"
            + " VALUES ('u', 'i', false, now() - interval '400 days')");

    try (Connection app = appConnection(null);
        var statement = app.createStatement();
        var result = statement.executeQuery("SELECT audit.purge_login_attempts()")) {
      result.next();
      assertThat(result.getLong(1)).isPositive();
      app.commit();
    }
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE action = 'PURGE'"
                    + " AND entity_type = 'LOGIN_ATTEMPTS'",
                Integer.class))
        .isPositive();
  }
}
