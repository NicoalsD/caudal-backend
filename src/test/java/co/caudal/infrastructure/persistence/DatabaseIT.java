package co.caudal.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base of the database integration tests (PostgreSQL 18 by Testcontainers, every migration
 * applied).
 *
 * <p>{@link #owner} runs as the container user, which owns the objects and is a superuser, so it
 * prepares data without RLS. Anything that checks privileges or RLS must use {@link #appConnection}
 * or {@link #readonlyConnection}, which log in as the real roles.
 */
@SpringBootTest
abstract class DatabaseIT {

  /** Password given to the roles only inside the throwaway test container. */
  private static final String ROLE_PASSWORD = "test-only-role-password";

  @Autowired protected JdbcTemplate owner;
  @Autowired private DataSource dataSource;

  private String url;

  @BeforeEach
  void enableRoleLogins() throws SQLException {
    owner.execute("ALTER ROLE caudal_app PASSWORD '" + ROLE_PASSWORD + "'");
    owner.execute("ALTER ROLE caudal_readonly PASSWORD '" + ROLE_PASSWORD + "'");
    try (Connection connection = dataSource.getConnection()) {
      url = connection.getMetaData().getURL();
    }
  }

  /** Opens a connection as caudal_app in a transaction, with the given aqueduct (or none). */
  protected Connection appConnection(UUID aqueductId) throws SQLException {
    Connection connection = DriverManager.getConnection(url, "caudal_app", ROLE_PASSWORD);
    connection.setAutoCommit(false);
    if (aqueductId != null) {
      try (var statement =
          connection.prepareStatement("SELECT set_config('app.aqueduct_id', ?, true)")) {
        statement.setString(1, aqueductId.toString());
        statement.execute();
      }
    }
    return connection;
  }

  /** Opens a connection as caudal_readonly. */
  protected Connection readonlyConnection() throws SQLException {
    return DriverManager.getConnection(url, "caudal_readonly", ROLE_PASSWORD);
  }

  /** Creates an aqueduct with a tank, as owner. Returns the aqueduct id. */
  protected UUID newAqueduct(boolean demo) {
    String slug = "prueba-" + UUID.randomUUID().toString().substring(0, 8);
    UUID aqueduct =
        owner.queryForObject(
            "INSERT INTO org.aqueducts (slug, name, municipality, department, is_demo) "
                + "VALUES (?, 'Vereda de Prueba', 'Guaitarilla', 'Nariño', ?) RETURNING id",
            UUID.class,
            slug,
            demo);
    owner.update(
        "INSERT INTO org.tanks (aqueduct_id, name, gauge_min, gauge_max, gauge_step) "
            + "VALUES (?, 'Tanque de prueba', 0, 5, 0.05)",
        aqueduct);
    return aqueduct;
  }

  /** Returns the tank of an aqueduct created by {@link #newAqueduct}. */
  protected UUID tankOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT id FROM org.tanks WHERE aqueduct_id = ? LIMIT 1", UUID.class, aqueduct);
  }

  /** Returns the system account created by the demo seed. */
  protected UUID systemUser() {
    return owner.queryForObject(
        "SELECT id FROM iam.users WHERE username = 'caudal.system'", UUID.class);
  }

  /** Creates an ACTIVE rule set for the aqueduct, as owner. Returns its id. */
  protected UUID activeRuleSet(UUID aqueduct) {
    UUID ruleSet =
        owner.queryForObject(
            "INSERT INTO org.rule_sets (aqueduct_id, version, reserve_level, max_daily_service_hours,"
                + " min_shift_hours, max_shift_hours, duplicate_window_minutes,"
                + " max_level_change_per_hour, stale_reading_hours, max_backdate_days,"
                + " forecast_horizon_days, forecast_context_days, trend_threshold_per_day,"
                + " created_by) VALUES (?, 1, 1, 16, 1, 4, 30, 0.5, 12, 7, 3, 30, 0.2, ?)"
                + " RETURNING id",
            UUID.class,
            aqueduct,
            systemUser());
    owner.update(
        "UPDATE org.rule_sets SET status = 'ACTIVE', change_reason = 'Reglas de prueba activas.'"
            + " WHERE id = ?",
        ruleSet);
    return ruleSet;
  }
}
