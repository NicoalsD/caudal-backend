package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** A person with memberships in two aqueducts switches between them. */
class SwitchAqueductIT extends SessionIT {

  @Test
  void theNewTokenCarriesTheOtherAqueductAndItsRole() throws Exception {
    Account person = account("switch.person", "OPERATOR");
    UUID second = newAqueductWithRules();
    newMembership(person.userId(), second, "BOARD_MEMBER");
    Session session = login(person);

    String token =
        mvc.perform(
                post("/api/v1/auth/switch-aqueduct")
                    .header("Authorization", session.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"aqueduct_id\":\"" + second + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");

    mvc.perform(get("/api/v1/rule-sets/current").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void anAqueductWithoutMembershipIsForbidden() throws Exception {
    Session session = login(account("switch.alone", "OPERATOR"));

    mvc.perform(
            post("/api/v1/auth/switch-aqueduct")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aqueduct_id\":\"" + newAqueduct() + "\"}"))
        .andExpect(status().isForbidden());
  }
}
