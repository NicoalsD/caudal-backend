package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.infrastructure.security.TestJwts;
import co.caudal.support.AppRoleIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** GET /auth/me with real tokens, the real database and the least-privilege role. */
class AuthMeIT extends AppRoleIT {

  @Autowired private MockMvc mvc;

  private String bearer(UUID user, UUID aqueduct, String role) {
    return "Bearer "
        + TestJwts.sign(
            TestJwts.validClaims(user, role, aqueduct, 0, Instant.now()).build(), TestJwts.SECRET);
  }

  @Test
  void returnsTheAccountItsActiveMembershipsAndThePermissionsOfTheRole() throws Exception {
    UUID user = newUser("me.operator", UNUSABLE_HASH);
    UUID aqueduct = newAqueduct();
    newMembership(user, aqueduct, "OPERATOR");
    newMembership(
        user,
        newAqueduct(),
        "BOARD_MEMBER",
        Instant.now().minusSeconds(7200),
        Instant.now().minusSeconds(3600));

    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(user, aqueduct, "OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("me.operator"))
        .andExpect(jsonPath("$.memberships.length()").value(1))
        .andExpect(jsonPath("$.memberships[0].aqueduct_id").value(aqueduct.toString()))
        .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("READING_CREATE")))
        .andExpect(
            jsonPath("$.permissions")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("USER_MANAGE"))));
  }

  @Test
  void isAllowedWhileThePasswordMustBeChanged() throws Exception {
    UUID user = newUser("me.mustchange", UNUSABLE_HASH);
    owner.update("UPDATE iam.users SET must_change_password = true WHERE id = ?", user);
    UUID aqueduct = newAqueduct();
    newMembership(user, aqueduct, "OPERATOR");

    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(user, aqueduct, "OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.must_change_password").value(true));
  }

  @Test
  void aStaleTokenVersionAnswers401() throws Exception {
    UUID user = newUser("me.stale", UNUSABLE_HASH);
    UUID aqueduct = newAqueduct();
    owner.update("UPDATE iam.users SET token_version = 5 WHERE id = ?", user);

    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(user, aqueduct, "OPERATOR")))
        .andExpect(status().isUnauthorized());
  }
}
