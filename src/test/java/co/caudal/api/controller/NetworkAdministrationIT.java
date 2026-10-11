package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Sectors and valves of the network end to end. */
class NetworkAdministrationIT extends SessionIT {

  private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    return mvc.perform(
        request
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String idOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  @Test
  void createsAndChangesASectorWithItsChild() throws Exception {
    Session session = login(account("net.sectors", "BOARD_ADMIN", newAqueductWithRules()));

    String parent =
        idOf(
            send(session, post("/api/v1/sectors"), "{\"code\":\"NORTE\",\"name\":\"Norte\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.households_count").value(0))
                .andExpect(jsonPath("$.is_active").value(true)));
    send(
            session,
            post("/api/v1/sectors"),
            "{\"code\":\"NORTE-A\",\"name\":\"Norte A\",\"parent_sector_id\":\""
                + parent
                + "\",\"households_count\":7}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.parent_sector_id").value(parent));
    send(
            session,
            patch("/api/v1/sectors/" + parent),
            "{\"households_count\":3,\"is_active\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NORTE"))
        .andExpect(jsonPath("$.households_count").value(3))
        .andExpect(jsonPath("$.is_active").value(false));
    mvc.perform(get("/api/v1/sectors").header("Authorization", session.bearer()))
        .andExpect(jsonPath("$.items.length()").value(4));
  }

  @Test
  void aRepeatedCodeAnUnknownParentAndAnOperatorAreRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account("net.refused", "BOARD_ADMIN", aqueduct));

    send(admin, post("/api/v1/sectors"), "{\"code\":\"ALTO\",\"name\":\"Otro Alto\"}")
        .andExpect(status().isConflict());
    send(
            admin,
            post("/api/v1/sectors"),
            "{\"code\":\"HIJO\",\"name\":\"Hijo\",\"parent_sector_id\":\""
                + UUID.randomUUID()
                + "\"}")
        .andExpect(status().isBadRequest());
    send(admin, post("/api/v1/sectors"), "{\"code\":\"bad code\",\"name\":\"Malo\"}")
        .andExpect(status().isBadRequest());
    send(admin, patch("/api/v1/sectors/" + UUID.randomUUID()), "{\"name\":\"Nadie\"}")
        .andExpect(status().isNotFound());
    send(
            login(account("net.operator", "OPERATOR", aqueduct)),
            post("/api/v1/sectors"),
            "{\"code\":\"OPER\",\"name\":\"Operador\"}")
        .andExpect(status().isForbidden());
  }
}
