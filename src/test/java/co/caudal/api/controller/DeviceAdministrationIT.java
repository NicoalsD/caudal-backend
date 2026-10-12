package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Registration and status changes of the field devices end to end (docs/API.md, section 16). */
class DeviceAdministrationIT extends SessionIT {

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

  private UUID tankOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT id FROM org.tanks WHERE aqueduct_id = ? AND name = 'Tanque principal'",
        UUID.class,
        aqueduct);
  }

  private UUID valveOf(UUID aqueduct, String code) {
    return owner.queryForObject(
        "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
            + " WHERE s.aqueduct_id = ? AND v.code = ?",
        UUID.class,
        aqueduct,
        code);
  }

  @Test
  void registersEachKindWithItsReference() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.register", "BOARD_ADMIN", aqueduct));
    UUID tank = tankOf(aqueduct);
    UUID valve = valveOf(aqueduct, "V-ALTO");

    String sensor =
        idOf(
            send(
                    session,
                    post("/api/v1/devices"),
                    "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Sensor principal\",\"tank_id\":\""
                        + tank
                        + "\",\"firmware_version\":\"1.4.2\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.kind").value("LEVEL_SENSOR"))
                .andExpect(jsonPath("$.tank_id").value(tank.toString()))
                .andExpect(jsonPath("$.valve_id").doesNotExist())
                .andExpect(jsonPath("$.firmware_version").value("1.4.2"))
                .andExpect(jsonPath("$.last_seen_at").doesNotExist()));
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"VALVE_ACTUATOR\",\"name\":\"Motor del puente\",\"valve_id\":\""
                + valve
                + "\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.valve_id").value(valve.toString()));
    send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"Gateway veredal\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tank_id").doesNotExist());

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.devices WHERE id = ? AND created_by = ?",
                Integer.class,
                UUID.fromString(sensor),
                session.userId()))
        .isEqualTo(1);
  }

  @Test
  void aReferenceThatDoesNotMatchTheKindIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.kinds", "BOARD_ADMIN", aqueduct));
    UUID tank = tankOf(aqueduct);
    UUID valve = valveOf(aqueduct, "V-ALTO");

    send(session, post("/api/v1/devices"), "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Sin tanque\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Con válvula\",\"tank_id\":\""
                + tank
                + "\",\"valve_id\":\""
                + valve
                + "\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"VALVE_ACTUATOR\",\"name\":\"Sin válvula\",\"tank_id\":\"" + tank + "\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"GATEWAY\",\"name\":\"Con tanque\",\"tank_id\":\"" + tank + "\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTankOrValveOfAnotherAqueductOrAnInactiveTankIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.foreign", "BOARD_ADMIN", aqueduct));
    UUID other = newAqueductWithRules();
    UUID foreignTank = tankOf(other);
    UUID foreignValve = valveOf(other, "V-ALTO");
    UUID tank = tankOf(aqueduct);
    owner.update("UPDATE org.tanks SET is_active = false WHERE id = ?", tank);

    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Ajeno\",\"tank_id\":\"" + foreignTank + "\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"VALVE_ACTUATOR\",\"name\":\"Ajena\",\"valve_id\":\""
                + foreignValve
                + "\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Tanque fuera de uso\",\"tank_id\":\""
                + tank
                + "\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidFieldsAreRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.fields", "BOARD_ADMIN", aqueduct));

    send(session, post("/api/v1/devices"), "{\"kind\":\"ROUTER\",\"name\":\"Malo\"}")
        .andExpect(status().isBadRequest());
    send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"X\"}")
        .andExpect(status().isBadRequest());
    send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"<script>\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"GATEWAY\",\"name\":\"Gateway\",\"firmware_version\":\""
                + "v".repeat(31)
                + "\"}")
        .andExpect(status().isBadRequest());
    send(
            session,
            post("/api/v1/devices"),
            "{\"kind\":\"GATEWAY\",\"name\":\"Gateway\",\"unknown\":true}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void changesTheStatusOfADeviceOfTheAqueduct() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.status", "BOARD_ADMIN", aqueduct));
    String device =
        idOf(
            send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"Gateway\"}")
                .andExpect(status().isCreated()));

    send(session, patch("/api/v1/devices/" + device), "{\"status\":\"SUSPENDED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(device))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));
    send(session, patch("/api/v1/devices/" + device), "{\"status\":\"RETIRED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"));
    send(session, patch("/api/v1/devices/" + device), "{\"status\":\"BROKEN\"}")
        .andExpect(status().isBadRequest());
    send(session, patch("/api/v1/devices/" + device), "{}").andExpect(status().isBadRequest());
  }

  @Test
  void aForeignOrUnknownDeviceIsNotFound() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("dev.isolation", "BOARD_ADMIN", aqueduct));
    UUID other = newAqueductWithRules();
    UUID foreign =
        owner.queryForObject(
            "INSERT INTO devices.devices (aqueduct_id, kind, name, created_by)"
                + " VALUES (?, 'GATEWAY', 'Gateway ajeno', (SELECT id FROM iam.users"
                + " WHERE username = 'caudal.system')) RETURNING id",
            UUID.class,
            other);

    send(session, patch("/api/v1/devices/" + foreign), "{\"status\":\"SUSPENDED\"}")
        .andExpect(status().isNotFound());
    send(session, patch("/api/v1/devices/" + UUID.randomUUID()), "{\"status\":\"SUSPENDED\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void anOperatorCannotManageDevices() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session operator = login(account("dev.operator", "OPERATOR", aqueduct));

    send(operator, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"Gateway\"}")
        .andExpect(status().isForbidden());
    send(operator, patch("/api/v1/devices/" + UUID.randomUUID()), "{\"status\":\"ACTIVE\"}")
        .andExpect(status().isForbidden());
  }
}
