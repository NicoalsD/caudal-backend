package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Changing accounts end to end: status rules, session revocation and aqueduct isolation. */
class UserAdministrationIT extends SessionIT {

  private ResultActions update(Session session, UUID user, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/users/" + user)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions loginAttempt(String username) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .with(
                request -> {
                  request.setRemoteAddr("198.51.100." + (1 + (int) (System.nanoTime() % 200)));
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"));
  }

  @Test
  void renamesAnAccountOfTheAqueduct() throws Exception {
    Account admin = account("admin.rename", "BOARD_ADMIN");
    Account operator = account("operator.rename", "OPERATOR", admin.aqueductId());

    update(login(admin), operator.userId(), "{\"full_name\":\"Persona  Renombrada\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.full_name").value("Persona Renombrada"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void lockingEndsTheSessionsUntilAnAdministratorUnlocks() throws Exception {
    Account admin = account("admin.lock", "BOARD_ADMIN");
    Account operator = account("operator.lock", "OPERATOR", admin.aqueductId());
    Session adminSession = login(admin);
    Session operatorSession = login(operator);

    update(adminSession, operator.userId(), "{\"status\":\"LOCKED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"));

    mvc.perform(get("/api/v1/auth/me").header("Authorization", operatorSession.bearer()))
        .andExpect(status().isUnauthorized());
    mvc.perform(refreshWith(operatorSession.refreshSecret())).andExpect(status().isUnauthorized());
    loginAttempt("operator.lock")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

    update(adminSession, operator.userId(), "{\"status\":\"ACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    loginAttempt("operator.lock").andExpect(status().isOk());
  }

  @Test
  void aDisabledAccountKeepsItsMomentAndNeverComesBack() throws Exception {
    Account admin = account("admin.disable", "BOARD_ADMIN");
    Account operator = account("operator.disable", "OPERATOR", admin.aqueductId());
    Session session = login(admin);

    update(session, operator.userId(), "{\"status\":\"DISABLED\"}").andExpect(status().isOk());
    Object disabledAt =
        owner.queryForObject(
            "SELECT disabled_at FROM iam.users WHERE id = ?", Object.class, operator.userId());
    update(session, operator.userId(), "{\"full_name\":\"Otra Persona\"}")
        .andExpect(status().isOk());

    Map<String, Object> row =
        owner.queryForMap(
            "SELECT status, disabled_at FROM iam.users WHERE id = ?", operator.userId());
    assertThat(row.get("status")).isEqualTo("DISABLED");
    assertThat(row.get("disabled_at")).isNotNull().isEqualTo(disabledAt);
    update(session, operator.userId(), "{\"status\":\"ACTIVE\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void anAdministratorCannotLockThemselves() throws Exception {
    Account admin = account("admin.self", "BOARD_ADMIN");

    update(login(admin), admin.userId(), "{\"status\":\"DISABLED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void anAccountOfAnotherAqueductIsNotFound() throws Exception {
    Account admin = account("admin.isolated", "BOARD_ADMIN");
    Account stranger = account("operator.stranger", "OPERATOR");

    update(login(admin), stranger.userId(), "{\"status\":\"LOCKED\"}")
        .andExpect(status().isNotFound());
    assertThat(
            owner.queryForObject(
                "SELECT status FROM iam.users WHERE id = ?", String.class, stranger.userId()))
        .isEqualTo("ACTIVE");
  }

  @Test
  void rejectsAnUnknownStatusAndARoleWithoutUserManage() throws Exception {
    Account admin = account("admin.invalid", "BOARD_ADMIN");
    Account operator = account("operator.invalid", "OPERATOR", admin.aqueductId());

    update(login(admin), operator.userId(), "{\"status\":\"PAUSED\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    update(login(operator), admin.userId(), "{\"status\":\"LOCKED\"}")
        .andExpect(status().isForbidden());
  }
}
