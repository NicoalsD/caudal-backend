package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.domain.user.ResetCode;
import co.caudal.support.SessionIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Creating accounts end to end: real hashes, row level security and the activation flow. */
class UserCreationIT extends SessionIT {

  private ResultActions create(Session session, String username, UUID aqueduct, String role)
      throws Exception {
    return mvc.perform(
        post("/api/v1/users")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"username\":\""
                    + username
                    + "\",\"full_name\":\"Persona  de Prueba\",\"aqueduct_id\":\""
                    + aqueduct
                    + "\",\"role\":\""
                    + role
                    + "\"}"));
  }

  private String codeOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"activation_code\":\"([^\"]+)\".*", "$1");
  }

  @Test
  void anAdministratorCreatesAnAccountThatActivatesWithItsCode() throws Exception {
    Account admin = account("users.admin", "BOARD_ADMIN");
    Session session = login(admin);

    ResultActions created =
        create(session, "New.Operator", admin.aqueductId(), "OPERATOR")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("new.operator"))
            .andExpect(jsonPath("$.full_name").value("Persona de Prueba"))
            .andExpect(jsonPath("$.must_change_password").value(true))
            .andExpect(jsonPath("$.membership.aqueduct_id").value(admin.aqueductId().toString()));
    String code = codeOf(created);

    Map<String, Object> user =
        owner.queryForMap(
            "SELECT id, password_hash, must_change_password, created_by FROM iam.users"
                + " WHERE username = 'new.operator'");
    assertThat(user.get("must_change_password")).isEqualTo(true);
    assertThat(user.get("created_by")).isEqualTo(admin.userId());
    assertThat((String) user.get("password_hash")).startsWith("$argon2id$");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.memberships WHERE user_id = ? AND aqueduct_id = ?"
                    + " AND role_code = 'OPERATOR' AND granted_by = ? AND valid_to IS NULL",
                Integer.class,
                user.get("id"),
                admin.aqueductId(),
                admin.userId()))
        .isEqualTo(1);
    Map<String, Object> grant =
        owner.queryForMap(
            "SELECT code_hash, issued_by, used_at FROM iam.password_reset_grants WHERE user_id = ?",
            user.get("id"));
    assertThat(grant.get("code_hash")).isEqualTo(ResetCode.parse(code).orElseThrow().hash());
    assertThat(grant.get("code_hash")).isNotEqualTo(code);
    assertThat(grant.get("issued_by")).isEqualTo(admin.userId());

    // Nobody knows the password: it cannot sign in. The code sets one, and then it can.
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new.operator\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"new.operator\",\"code\":\""
                        + code
                        + "\",\"new_password\":\"clave-de-activacion-larga\"}"))
        .andExpect(status().isNoContent());
    Session activated =
        login(
            new Account((UUID) user.get("id"), admin.aqueductId(), "new.operator"),
            "clave-de-activacion-larga");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", activated.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.must_change_password").value(true))
        .andExpect(jsonPath("$.memberships[0].role").value("OPERATOR"));
  }

  @Test
  void aBoardMemberCannotCreateUsersAndTheDenialIsRecorded() throws Exception {
    Account member = account("users.member", "BOARD_MEMBER");
    Session session = login(member);

    create(session, "never.created", member.aqueductId(), "OPERATOR")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.users WHERE username = 'never.created'", Integer.class))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE type = 'PERMISSION_DENIED'"
                    + " AND actor_user_id = ?",
                Integer.class,
                member.userId()))
        .isEqualTo(1);
  }

  @Test
  void anOperatorAndSupportEntityCannotEither() throws Exception {
    for (String role : new String[] {"OPERATOR", "PROJECT_TEAM", "SUPPORT_ENTITY"}) {
      Account other = account("users.denied." + role.toLowerCase().replace('_', '-'), role);
      create(login(other), "never.created." + role.length(), other.aqueductId(), "OPERATOR")
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void aTakenUsernameIsAConflictAndLeavesNoGrantBehind() throws Exception {
    Account admin = account("users.conflict", "BOARD_ADMIN");
    Session session = login(admin);
    create(session, "taken.name", admin.aqueductId(), "OPERATOR").andExpect(status().isCreated());

    create(session, "Taken.Name", admin.aqueductId(), "OPERATOR")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.password_reset_grants g JOIN iam.users u ON u.id ="
                    + " g.user_id WHERE u.username = 'taken.name'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void anAqueductOtherThanTheSessionsIsForbiddenAndNothingIsCreated() throws Exception {
    Account admin = account("users.cross", "BOARD_ADMIN");
    Session session = login(admin);

    create(session, "cross.tenant", newAqueduct(), "OPERATOR")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.users WHERE username = 'cross.tenant'", Integer.class))
        .isZero();
  }

  @Test
  void anUnknownRoleAndABadUsernameAreValidationErrors() throws Exception {
    Account admin = account("users.invalid", "BOARD_ADMIN");
    Session session = login(admin);

    create(session, "ghost.role", admin.aqueductId(), "GHOST_ROLE")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("role"));
    create(session, "Bad Name!", admin.aqueductId(), "OPERATOR")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("username"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.users WHERE username IN ('ghost.role')", Integer.class))
        .isZero();
  }
}
