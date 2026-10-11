package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Imports of simulated history: demo only, age exemption, file idempotency. */
class ImportIT extends SessionIT {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  private static String row(UUID tank, String value, Instant at) {
    return "{\"id\":\""
        + UUID.randomUUID()
        + "\",\"tank_id\":\""
        + tank
        + "\",\"gauge_value\":"
        + value
        + ",\"water_appearance_code\":\"NORMAL\",\"observed_at\":\""
        + at
        + "\"}";
  }

  private static String sha() {
    return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
  }

  ResultActions importReadings(Session session, UUID aqueduct, String sha, List<String> rows)
      throws Exception {
    return mvc.perform(
        post("/api/v1/imports/readings")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"aqueduct_id\":\""
                    + aqueduct
                    + "\",\"scenario_name\":\"prueba\",\"seed\":7,\"simulator_version\":\"0.1\","
                    + "\"file_sha256\":\""
                    + sha
                    + "\",\"rows\":["
                    + rows.stream().collect(Collectors.joining(","))
                    + "]}"));
  }

  @Test
  void oldSimulatedHistoryIsStoredAndBadRowsAreReported() throws Exception {
    UUID demo = demoAqueduct();
    UUID tank = newTank(demo);
    Session team = login(account("import.team", "PROJECT_TEAM", demo));
    String sha = sha();

    String body =
        importReadings(
                team,
                demo,
                sha,
                List.of(
                    row(tank, "3.00", NOW.minus(40, ChronoUnit.DAYS)),
                    row(tank, "9.00", NOW.minus(39, ChronoUnit.DAYS)),
                    row(tank, "2.90", NOW.minus(38, ChronoUnit.DAYS))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rows_received").value(3))
            .andExpect(jsonPath("$.rows_accepted").value(2))
            .andExpect(jsonPath("$.rows_rejected").value(1))
            .andExpect(jsonPath("$.rejections[0].row").value(2))
            .andExpect(jsonPath("$.rejections[0].code").value("GAUGE_OUT_OF_RANGE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID importId = UUID.fromString(body.replaceAll(".*\"import_batch_id\":\"([^\"]+)\".*", "$1"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.readings WHERE import_batch_id = ? AND source = 'IMPORT'",
                Integer.class,
                importId))
        .isEqualTo(2);
    importReadings(team, demo, sha, List.of(row(tank, "2.00", NOW.minus(1, ChronoUnit.HOURS))))
        .andExpect(status().isConflict());
  }

  @Test
  void aRealAqueductIsDemoOnlyAndAnOperatorCannotImport() throws Exception {
    UUID real = newAqueductWithRules();
    UUID tank = newTank(real);
    Session team = login(account("import.real", "PROJECT_TEAM", real));

    importReadings(team, real, sha(), List.of(row(tank, "2.00", NOW.minusSeconds(600))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("DEMO_ONLY"));
    Session operator = login(account("import.operator", "OPERATOR", demoAqueduct()));
    importReadings(
            operator, demoAqueduct(), sha(), List.of(row(tank, "2.00", NOW.minusSeconds(600))))
        .andExpect(status().isForbidden());
  }
}
