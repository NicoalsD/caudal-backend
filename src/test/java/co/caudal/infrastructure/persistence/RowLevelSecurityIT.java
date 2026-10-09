package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RowLevelSecurityIT extends DatabaseIT {

  private static final int TABLES_WITH_RLS = 37;

  private static int count(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getInt(1);
    }
  }

  @Test
  void forcesRlsOnTheDocumentedTables() {
    Integer forced =
        owner.queryForObject(
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                + " WHERE c.relkind = 'r' AND c.relrowsecurity AND c.relforcerowsecurity"
                + " AND n.nspname IN ('iam','org','ops','reporting','devices','audit','sim')",
            Integer.class);

    assertThat(forced).isEqualTo(TABLES_WITH_RLS);
  }

  @Test
  void withoutAqueductNoRowIsVisible() throws SQLException {
    newAqueduct(false);

    try (Connection app = appConnection(null)) {
      assertThat(count(app, "SELECT count(*) FROM org.tanks")).isZero();
      assertThat(count(app, "SELECT count(*) FROM ops.readings")).isZero();
    }
  }

  @Test
  void anAqueductOnlySeesItsOwnRows() throws SQLException {
    UUID first = newAqueduct(false);
    UUID second = newAqueduct(false);

    try (Connection app = appConnection(first)) {
      assertThat(count(app, "SELECT count(*) FROM org.tanks")).isEqualTo(1);
      try (var statement =
          app.prepareStatement("SELECT count(*) FROM org.tanks WHERE aqueduct_id = ?")) {
        statement.setObject(1, second);
        var result = statement.executeQuery();
        result.next();
        assertThat(result.getInt(1)).isZero();
      }
    }
  }

  @Test
  void withCheckRejectsRowsOfAnotherAqueduct() throws SQLException {
    UUID first = newAqueduct(false);
    UUID second = newAqueduct(false);

    try (Connection app = appConnection(first);
        var statement =
            app.prepareStatement(
                "INSERT INTO org.sectors (aqueduct_id, code, name) VALUES (?, 'S1', 'Sector')")) {
      statement.setObject(1, second);
      assertThatThrownBy(statement::executeUpdate).hasStackTraceContaining("row-level security");
    }
  }

  @Test
  void compositeForeignKeyBlocksReferencesAcrossAqueducts() {
    UUID first = newAqueduct(false);
    UUID second = newAqueduct(false);
    UUID ruleSet = activeRuleSet(first);

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value,"
                        + " water_appearance_code, observed_at, source, rule_set_id,"
                        + " validation_status) VALUES (uuidv7(), ?, ?, 2, 'NORMAL', now(),"
                        + " 'MANUAL_APP', ?, 'ACCEPTED')",
                    first,
                    tankOf(second),
                    ruleSet))
        .hasStackTraceContaining("readings_tank_id_fk");
  }

  @Test
  void childTablesFollowTheirParent() throws SQLException {
    UUID first = newAqueduct(false);
    UUID second = newAqueduct(false);
    UUID sector =
        owner.queryForObject(
            "INSERT INTO org.sectors (aqueduct_id, code, name) VALUES (?, 'S1', 'Sector uno')"
                + " RETURNING id",
            UUID.class,
            second);
    owner.update(
        "INSERT INTO org.valves (sector_id, code, name) VALUES (?, 'V-1', 'Válvula uno')", sector);

    try (Connection app = appConnection(first)) {
      assertThat(count(app, "SELECT count(*) FROM org.valves WHERE sector_id = '" + sector + "'"))
          .isZero();
    }
  }

  @Test
  void membershipsAreVisibleToTheirOwnUserAtLogin() throws SQLException {
    UUID aqueduct = newAqueduct(false);
    UUID user = systemUser();
    owner.update(
        "INSERT INTO iam.memberships (user_id, aqueduct_id, role_code, granted_by)"
            + " VALUES (?, ?, 'BOARD_MEMBER', ?)",
        user,
        aqueduct,
        user);

    try (Connection app = appConnection(null)) {
      try (var statement = app.prepareStatement("SELECT set_config('app.user_id', ?, true)")) {
        statement.setString(1, user.toString());
        statement.execute();
      }
      assertThat(
              count(
                  app,
                  "SELECT count(*) FROM iam.memberships WHERE aqueduct_id = '" + aqueduct + "'"))
          .isEqualTo(1);
    }
  }
}
