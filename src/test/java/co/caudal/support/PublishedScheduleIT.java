package co.caudal.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers to reach an approved or published schedule through the API. */
public abstract class PublishedScheduleIT extends SessionIT {

  /** The day the schedules are made for. */
  protected static final LocalDate TOMORROW =
      LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);

  /** Stores a recent accepted reading of the first tank of the aqueduct. */
  protected void reading(UUID aqueduct, String value) {
    Instant at = Instant.now().minus(1, ChronoUnit.HOURS);
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " SELECT gen_random_uuid(), t.aqueduct_id, t.id, ?::numeric, 'NORMAL', ?, ?,"
            + " 'IMPORT', r.id, 'ACCEPTED' FROM org.tanks t JOIN org.rule_sets r"
            + " ON r.aqueduct_id = t.aqueduct_id AND r.status = 'ACTIVE'"
            + " WHERE t.aqueduct_id = ? ORDER BY t.name LIMIT 1",
        value,
        Timestamp.from(at),
        Timestamp.from(at),
        aqueduct);
  }

  /** Posts to an action of a proposal. */
  protected ResultActions decide(Session session, String id, String action, String body)
      throws Exception {
    return mvc.perform(
        post("/api/v1/schedule-proposals/" + id + "/" + action)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** Generates the proposal of tomorrow and approves it; returns its id. */
  protected String approvedProposal(Session member) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/schedule-proposals")
                    .header("Authorization", member.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"service_date\":\"" + TOMORROW + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");
    decide(member, id, "approve", "{\"lock_version\":0}");
    return id;
  }

  /** Publishes the approved proposal of tomorrow; returns the publication JSON. */
  protected String publishedSchedule(Session member) throws Exception {
    String id = approvedProposal(member);
    return decide(member, id, "publish", "{\"lock_version\":1}")
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
