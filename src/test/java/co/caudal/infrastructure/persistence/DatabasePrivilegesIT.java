package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabasePrivilegesIT extends DatabaseIT {

  @Test
  void rolesHaveNoElevatedAttributes() {
    Integer elevated =
        owner.queryForObject(
            "SELECT count(*) FROM pg_roles WHERE rolname IN ('caudal_app', 'caudal_readonly')"
                + " AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)",
            Integer.class);

    assertThat(elevated).isZero();
  }

  @Test
  void roleTimeoutsMatchTheDocument() {
    List<String> settings =
        owner.queryForList(
            "SELECT unnest(s.setconfig) FROM pg_db_role_setting s JOIN pg_roles r"
                + " ON r.oid = s.setrole WHERE r.rolname = 'caudal_app'",
            String.class);

    assertThat(settings)
        .contains(
            "statement_timeout=5s", "lock_timeout=3s", "idle_in_transaction_session_timeout=10s");
  }

  @Test
  void applicationHasNoDdlNorDelete() throws SQLException {
    try (Connection app = appConnection(null);
        var statement = app.createStatement()) {
      assertThatThrownBy(() -> statement.execute("CREATE TABLE public.intruder (id int)"))
          .hasStackTraceContaining("permission denied");
    }
    assertThat(
            owner.queryForObject(
                "SELECT has_table_privilege('caudal_app', 'org.tanks', 'DELETE')", Boolean.class))
        .isFalse();
    assertThat(
            owner.queryForObject(
                "SELECT has_table_privilege('caudal_app', 'ops.readings', 'UPDATE')",
                Boolean.class))
        .isFalse();
  }

  @Test
  void applicationOnlyUpdatesTheAllowedColumns() {
    assertThat(
            owner.queryForObject(
                "SELECT has_column_privilege('caudal_app', 'iam.users', 'username', 'UPDATE')",
                Boolean.class))
        .isFalse();
    assertThat(
            owner.queryForObject(
                "SELECT has_column_privilege('caudal_app', 'iam.refresh_tokens', 'token_hash', 'UPDATE')",
                Boolean.class))
        .isFalse();
    assertThat(
            owner.queryForObject(
                "SELECT has_column_privilege('caudal_app', 'org.aqueducts', 'is_demo', 'UPDATE')",
                Boolean.class))
        .isFalse();
  }

  @Test
  void readonlyCannotReachAccountsNorSecrets() throws SQLException {
    try (Connection readonly = readonlyConnection();
        var statement = readonly.createStatement()) {
      assertThatThrownBy(() -> statement.executeQuery("SELECT password_hash FROM iam.users"))
          .hasStackTraceContaining("permission denied");
      assertThatThrownBy(() -> statement.executeQuery("SELECT token_hash FROM iam.refresh_tokens"))
          .hasStackTraceContaining("permission denied");
      assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM ops.readings"))
          .hasStackTraceContaining("permission denied");
    }
  }

  @Test
  void demoSeedIsMarkedAsSimulated() {
    assertThat(
            owner.queryForObject(
                "SELECT is_demo FROM org.aqueducts WHERE slug = 'vereda-demo'", Boolean.class))
        .isTrue();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.readings r JOIN org.aqueducts a ON a.id = r.aqueduct_id"
                    + " WHERE a.slug = 'vereda-demo' AND r.import_batch_id IS NOT NULL",
                Integer.class))
        .isPositive();
  }

  @Test
  void schemaHasTheDocumentedTables() {
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_type = 'BASE TABLE'"
                    + " AND table_schema IN ('iam','org','ops','reporting','devices','audit','sim')",
                Integer.class))
        .isEqualTo(57);
  }
}
