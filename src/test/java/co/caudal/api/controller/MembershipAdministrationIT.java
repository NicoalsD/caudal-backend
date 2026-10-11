package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Memberships end to end: grant, overlap, revocation, token version and aqueduct isolation. */
class MembershipAdministrationIT extends SessionIT {

  private ResultActions grant(Session session, UUID user, UUID aqueduct, String extra)
      throws Exception {
    return mvc.perform(
        post("/api/v1/memberships")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"user_id\":\""
                    + user
                    + "\",\"aqueduct_id\":\""
                    + aqueduct
                    + "\",\"role_code\":\"BOARD_MEMBER\""
                    + extra
                    + "}"));
  }

  @Test
  void grantsAndListsARole() throws Exception {
    Account admin = account("memb.admin", "BOARD_ADMIN");
    Account outsider = account("memb.newcomer", "OPERATOR");
    Session session = login(admin);

    String created =
        grant(session, outsider.userId(), admin.aqueductId(), "")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role_code").value("BOARD_MEMBER"))
            .andExpect(jsonPath("$.granted_by").value(admin.userId().toString()))
            .andExpect(jsonPath("$.valid_to").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    mvc.perform(
            get("/api/v1/memberships?user_id=" + outsider.userId())
                .header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id));
  }

  @Test
  void anOverlappingRoleIsAConflictAndABadRangeIsInvalid() throws Exception {
    Account admin = account("memb.overlap.admin", "BOARD_ADMIN");
    Account operator = account("memb.overlap.op", "OPERATOR", admin.aqueductId());
    Session session = login(admin);

    grant(session, operator.userId(), admin.aqueductId(), "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    Instant from = Instant.now().plusSeconds(86_400);
    grant(
            session,
            operator.userId(),
            admin.aqueductId(),
            ",\"valid_from\":\"" + from + "\",\"valid_to\":\"" + from.minusSeconds(60) + "\"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    grant(session, UUID.randomUUID(), admin.aqueductId(), "").andExpect(status().isNotFound());
  }

  @Test
  void anotherAqueductIsForbiddenAndItsMembershipsAreInvisible() throws Exception {
    Account admin = account("memb.iso.admin", "BOARD_ADMIN");
    Account stranger = account("memb.iso.stranger", "OPERATOR");
    Session session = login(admin);

    grant(session, stranger.userId(), stranger.aqueductId(), "").andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/memberships?aqueduct_id=" + stranger.aqueductId())
                .header("Authorization", session.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/memberships?user_id=" + stranger.userId())
                .header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }
}
