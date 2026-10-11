package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.domain.incident.TrackingCode;
import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Damage reports from the public page and their tracking. */
class DamageReportIT extends SessionIT {

  String slugOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT slug FROM org.aqueducts WHERE id = ?", String.class, aqueduct);
  }

  static String body(String category, String description, String extra) {
    return "{\"category_code\":\""
        + category
        + "\",\"description\":\""
        + description
        + "\",\"form_started_at\":\""
        + OffsetDateTime.now().minusMinutes(1)
        + "\""
        + extra
        + "}";
  }

  ResultActions report(String slug, String body, String ip) throws Exception {
    return mvc.perform(
        post("/api/v1/public/" + slug + "/damage-reports")
            .with(
                request -> {
                  request.setRemoteAddr(ip);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void anyoneReportsDamageAndGetsACodeShownOnce() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID sector =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = 'ALTO'",
            UUID.class,
            aqueduct);

    String answer =
        report(
                slugOf(aqueduct),
                body(
                    "LEAK",
                    "Hay agua saliendo del tubo\\r\\nde la calle principal.",
                    ",\"sector_id\":\"" + sector + "\",\"location_hint\":\" Esquina \""),
                "203.0.113.10")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REPORTED"))
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(answer, "$.tracking_code");

    assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{8}");
    assertThat((String) JsonPath.read(answer, "$.message")).contains(code);
    Map<String, Object> row =
        owner.queryForMap(
            "SELECT source, status, description, location_hint, tracking_code_hash,"
                + " reporter_ip_hmac FROM ops.incidents WHERE aqueduct_id = ?",
            aqueduct);
    assertThat(row)
        .containsEntry("source", "PUBLIC_FORM")
        .containsEntry("status", "REPORTED")
        .containsEntry("description", "Hay agua saliendo del tubo\nde la calle principal.")
        .containsEntry("location_hint", "Esquina")
        .containsEntry("tracking_code_hash", new TrackingCode(code).hash());
    assertThat((String) row.get("reporter_ip_hmac")).isNotBlank().doesNotContain("203.0.113.10");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.incident_status_history WHERE aqueduct_id = ?",
                Integer.class,
                aqueduct))
        .isEqualTo(1);
  }

  @Test
  void badReportsAreRefused() throws Exception {
    String slug = slugOf(newAqueductWithRules());

    report(slug, body("NOT_A_CATEGORY", "Hay agua saliendo del tubo.", ""), "203.0.113.11")
        .andExpect(status().isBadRequest());
    report(slug, body("LEAK", "corto", ""), "203.0.113.11").andExpect(status().isBadRequest());
    report(slug, body("LEAK", "Hay agua saliendo\u202e del tubo.", ""), "203.0.113.11")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.details.fields[0].code").value("FIELD_CHARACTERS_NOT_ALLOWED"));
    report(
            slug,
            body(
                "LEAK",
                "Hay agua saliendo del tubo.",
                ",\"sector_id\":\"" + UUID.randomUUID() + "\""),
            "203.0.113.11")
        .andExpect(status().isBadRequest());
    report(slug, body("LEAK", "Hay agua saliendo del tubo.", ",\"name\":\"Ana\""), "203.0.113.11")
        .andExpect(status().isBadRequest());
    report("no-existe", body("LEAK", "Hay agua saliendo del tubo.", ""), "203.0.113.11")
        .andExpect(status().isNotFound());
  }
}
