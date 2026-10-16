package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The privacy notice and its acceptance. */
class PrivacyNoticeIT extends SessionIT {

  private ResultActions accept(Session session, int version) throws Exception {
    return mvc.perform(
        post("/api/v1/privacy-notice/accept")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":" + version + "}"));
  }

  @Test
  void aPersonReadsAndAcceptsTheNoticeOnce() throws Exception {
    Session session = login(account("privacy.reader", "OPERATOR"));

    mvc.perform(get("/api/v1/privacy-notice/current").header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.text_sha256").value(org.hamcrest.Matchers.hasLength(64)));
    String first =
        accept(session, 1)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user_id").value(session.userId().toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    accept(session, 1)
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.accepted_at")
                .value(first.replaceAll(".*\"accepted_at\":\"([^\"]+)\".*", "$1")));
    accept(session, 2).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/auth/current-user").header("Authorization", session.bearer()))
        .andExpect(jsonPath("$.privacy_accepted_version").value(1));
  }

  @Test
  void anAnonymousCallerIsRefused() throws Exception {
    mvc.perform(get("/api/v1/privacy-notice/current")).andExpect(status().isUnauthorized());
  }
}
