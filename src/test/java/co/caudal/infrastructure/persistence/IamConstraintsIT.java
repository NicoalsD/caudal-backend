package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.FieldLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IamConstraintsIT extends DatabaseIT {

  private static final String INSERT_USER =
      "INSERT INTO iam.users (username, full_name, password_hash, aqueduct_id)"
          + " VALUES (?, ?, 'x', ?) RETURNING id";

  private UUID newUser(String username) {
    return newUser(username, null);
  }

  private UUID newUser(String username, UUID aqueduct) {
    return owner.queryForObject(INSERT_USER, UUID.class, username, "Persona de Prueba", aqueduct);
  }

  @Test
  void acceptsUsernameThatMatchesFieldLimits() {
    assertThat(newUser("test." + UUID.randomUUID().toString().substring(0, 8))).isNotNull();
  }

  @Test
  void rejectsUsernameWithUpperCaseOrTooShort() {
    assertThatThrownBy(() -> newUser("Test.Operator"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("users_username_format");
    assertThatThrownBy(() -> newUser("a".repeat(FieldLimits.USERNAME_MIN - 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateUsernameInsideOneInstitution() {
    UUID aqueduct =
        owner.queryForObject(
            "INSERT INTO org.aqueducts (slug, name, municipality, department)"
                + " VALUES (?, 'Vereda de Prueba', 'Guaitarilla', 'Nariño') RETURNING id",
            UUID.class,
            "dup-" + UUID.randomUUID().toString().substring(0, 8));
    String username = "dup." + UUID.randomUUID().toString().substring(0, 8);
    newUser(username, aqueduct);

    assertThatThrownBy(() -> newUser(username, aqueduct))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("users_aqueduct_username_uq");
  }

  @Test
  void allowsTheSameUsernameInTwoInstitutions() {
    String username = "same." + UUID.randomUUID().toString().substring(0, 8);
    UUID first = newAqueductRow();
    UUID second = newAqueductRow();

    assertThat(newUser(username, first)).isNotEqualTo(newUser(username, second));
  }

  @Test
  void rejectsDuplicatePlatformUsernameWhenNoInstitution() {
    String username = "platform." + UUID.randomUUID().toString().substring(0, 8);
    newUser(username);

    assertThatThrownBy(() -> newUser(username))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("users_platform_username_uq");
  }

  private UUID newAqueductRow() {
    return owner.queryForObject(
        "INSERT INTO org.aqueducts (slug, name, municipality, department)"
            + " VALUES (?, 'Vereda de Prueba', 'Guaitarilla', 'Nariño') RETURNING id",
        UUID.class,
        "uniq-" + UUID.randomUUID().toString().substring(0, 8));
  }

  @Test
  void rejectsUnknownStatusAndNegativeCounters() {
    UUID user = newUser("st." + UUID.randomUUID().toString().substring(0, 8));

    assertThatThrownBy(
            () -> owner.update("UPDATE iam.users SET status = 'BANNED' WHERE id = ?", user))
        .hasStackTraceContaining("users_status_values");
    assertThatThrownBy(
            () -> owner.update("UPDATE iam.users SET failed_login_count = -1 WHERE id = ?", user))
        .hasStackTraceContaining("users_failed_login_count_range");
  }

  @Test
  void refreshTokenMustExpireAfterIssue() {
    UUID user = newUser("rt." + UUID.randomUUID().toString().substring(0, 8));

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO iam.refresh_tokens (user_id, family_id, token_hash, issued_at,"
                        + " expires_at) VALUES (?, uuidv7(), repeat('a', 64), now(), now())",
                    user))
        .hasStackTraceContaining("refresh_tokens_expires_at_rule");
  }

  @Test
  void membershipNeedsKnownRoleAndValidPeriod() {
    UUID user = newUser("mb." + UUID.randomUUID().toString().substring(0, 8));
    UUID aqueduct = newAqueduct(false);

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO iam.memberships (user_id, aqueduct_id, role_code, granted_by)"
                        + " VALUES (?, ?, 'ADMIN', ?)",
                    user,
                    aqueduct,
                    user))
        .hasStackTraceContaining("memberships_role_code_fk");
    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO iam.memberships (user_id, aqueduct_id, role_code, granted_by,"
                        + " valid_from, valid_to) VALUES (?, ?, 'OPERATOR', ?, now(), now())",
                    user,
                    aqueduct,
                    user))
        .hasStackTraceContaining("memberships_valid_to_rule");
  }

  @Test
  void seedsTheRoleMatrix() {
    assertThat(owner.queryForObject("SELECT count(*) FROM iam.roles", Integer.class)).isEqualTo(7);
    // The new admin role gets the same permissions as the old board administrator.
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM (SELECT permission_code FROM iam.role_permissions"
                    + " WHERE role_code = 'BOARD_ADMIN' EXCEPT SELECT permission_code FROM"
                    + " iam.role_permissions WHERE role_code = 'INSTITUTION_ADMIN') d",
                Integer.class))
        .isZero();
    // The viewer only reads: no permission of the viewer ends in _MANAGE, _CREATE, _DECIDE or the
    // like.
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.role_permissions WHERE role_code = 'VIEWER'"
                    + " AND (permission_code LIKE '%\\_CREATE' OR permission_code LIKE '%\\_MANAGE'"
                    + " OR permission_code LIKE '%\\_DECIDE' OR permission_code LIKE '%\\_CORRECT'"
                    + " OR permission_code LIKE '%\\_COMMAND_MANUAL')",
                Integer.class))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.role_permissions WHERE role_code = 'BOARD_ADMIN'"
                    + " AND permission_code = 'IMPORT_SIMULATED'",
                Integer.class))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.role_permissions WHERE role_code = 'SUPPORT_ENTITY'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void foreignKeysRestrictDeletes() {
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace"
                    + " WHERE c.contype = 'f' AND c.confdeltype <> 'r'"
                    + " AND n.nspname IN ('iam','org','ops','reporting','devices','audit','sim')",
                Integer.class))
        .isZero();
  }
}
