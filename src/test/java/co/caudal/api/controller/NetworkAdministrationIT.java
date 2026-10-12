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

  @Test
  void addsAndChangesAValveOfASector() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("net.valves", "BOARD_ADMIN", aqueduct));
    UUID sector =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = 'ALTO'",
            UUID.class,
            aqueduct);

    String valve =
        idOf(
            send(
                    session,
                    post("/api/v1/sectors/" + sector + "/valves"),
                    "{\"code\":\"V-NUEVA\",\"name\":\"Válvula nueva\",\"location_hint\":"
                        + "\"Junto al puente\",\"is_motorized\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fail_safe_position").value("KEEP"))
                .andExpect(jsonPath("$.is_motorized").value(true)));
    send(
            session,
            patch("/api/v1/valves/" + valve),
            "{\"fail_safe_position\":\"CLOSED\",\"location_hint\":\"\",\"is_active\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("V-NUEVA"))
        .andExpect(jsonPath("$.fail_safe_position").value("CLOSED"))
        .andExpect(jsonPath("$.location_hint").doesNotExist())
        .andExpect(jsonPath("$.is_active").value(false));
    send(
            session,
            post("/api/v1/sectors/" + sector + "/valves"),
            "{\"code\":\"V-ALTO\",\"name\":\"Repetida\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void aValveOfAnotherAqueductIsInvisible() throws Exception {
    Session session = login(account("net.valves.iso", "BOARD_ADMIN", newAqueductWithRules()));
    UUID other = newAqueductWithRules();
    UUID foreignSector =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = 'ALTO'",
            UUID.class,
            other);
    UUID foreignValve =
        owner.queryForObject(
            "SELECT id FROM org.valves WHERE sector_id = ?", UUID.class, foreignSector);

    send(session, patch("/api/v1/valves/" + foreignValve), "{\"is_active\":false}")
        .andExpect(status().isNotFound());
    send(
            session,
            post("/api/v1/sectors/" + foreignSector + "/valves"),
            "{\"code\":\"X\",\"name\":\"Ajena\"}")
        .andExpect(status().isNotFound());
    send(session, patch("/api/v1/valves/" + foreignValve), "{\"fail_safe_position\":\"MAYBE\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void theTreeAddsUpHouseholdsAndValvesBySector() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("net.tree", "OPERATOR", aqueduct));
    UUID alto =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = 'ALTO'",
            UUID.class,
            aqueduct);
    owner.update(
        "INSERT INTO org.sectors (aqueduct_id, code, name, parent_sector_id, households_count)"
            + " VALUES (?, 'ALTO-1', 'Alto uno', ?, 4)",
        aqueduct,
        alto);

    mvc.perform(get("/api/v1/network").header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].code").value("ALTO"))
        .andExpect(jsonPath("$.items[0].households").value(14))
        .andExpect(jsonPath("$.items[0].active_valves").value(1))
        .andExpect(jsonPath("$.items[0].children[0].kind").value("VALVE"))
        .andExpect(jsonPath("$.items[0].children[1].code").value("ALTO-1"));
  }
}
