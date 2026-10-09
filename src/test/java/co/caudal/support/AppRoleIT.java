package co.caudal.support;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base of the integration tests that must run as the real application role.
 *
 * <p>The whole application connects as {@code caudal_app} (profile {@code app-role}), so column
 * privileges, the missing {@code BYPASSRLS} and row level security behave as in production. {@link
 * #owner} is a second connection as the container owner (a superuser) that prepares data without
 * RLS: users, aqueducts and memberships are created with direct SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "app-role"})
public abstract class AppRoleIT {

  private static final String OWNER_USER = "test";
  private static final String OWNER_PASSWORD = "test";
  private static final String APP_ROLE = "caudal_app";
  private static final String APP_ROLE_PASSWORD = "test-only-role-password";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18")
          .withDatabaseName("caudal")
          .withUsername(OWNER_USER)
          .withPassword(OWNER_PASSWORD);

  static {
    POSTGRES.start();
    Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
  }

  /** Points the datasource at the container as caudal_app and Flyway as the owner. */
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> APP_ROLE);
    registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", () -> OWNER_USER);
    registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
  }

  /** Hash that no password matches; tests that need a real hash pass their own. */
  protected static final String UNUSABLE_HASH =
      "$argon2id$v=19$m=64,t=1,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

  /** Direct SQL as the container owner (bypasses RLS). */
  protected JdbcTemplate owner;

  @BeforeEach
  void connectAsOwner() {
    if (owner == null) {
      owner =
          new JdbcTemplate(
              new DriverManagerDataSource(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD));
    }
  }

  /** Creates an ACTIVE account with the given hash. Returns its id. */
  protected UUID newUser(String username, String passwordHash) {
    return owner.queryForObject(
        "INSERT INTO iam.users (username, full_name, password_hash, must_change_password)"
            + " VALUES (?, 'Persona de Prueba Uno', ?, false) RETURNING id",
        UUID.class,
        username,
        passwordHash);
  }

  /** Creates an aqueduct (not demo) with a tank. Returns its id. */
  protected UUID newAqueduct() {
    String slug = "prueba-" + UUID.randomUUID().toString().substring(0, 8);
    return owner.queryForObject(
        "INSERT INTO org.aqueducts (slug, name, municipality, department, is_demo)"
            + " VALUES (?, 'Vereda de Prueba', 'Guaitarilla', 'Nariño', false) RETURNING id",
        UUID.class,
        slug);
  }

  /** Grants a role to an account in an aqueduct, valid from the given instant. */
  protected UUID newMembership(
      UUID user, UUID aqueduct, String role, Instant validFrom, Instant validTo) {
    UUID grantedBy =
        owner.queryForObject(
            "SELECT id FROM iam.users WHERE username = 'caudal.system'", UUID.class);
    return owner.queryForObject(
        "INSERT INTO iam.memberships (user_id, aqueduct_id, role_code, valid_from, valid_to,"
            + " granted_by) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        user,
        aqueduct,
        role,
        java.sql.Timestamp.from(validFrom),
        validTo == null ? null : java.sql.Timestamp.from(validTo),
        grantedBy);
  }

  /** Adds an open membership that started in the past. */
  protected UUID newMembership(UUID user, UUID aqueduct, String role) {
    return newMembership(user, aqueduct, role, Instant.now().minusSeconds(3600), null);
  }
}
