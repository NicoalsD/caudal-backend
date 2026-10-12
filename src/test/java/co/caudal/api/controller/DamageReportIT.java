package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    return body(category, description, OffsetDateTime.now().minusMinutes(1), extra);
  }

  static String body(String category, String description, OffsetDateTime started, String extra) {
    return "{\"category_code\":\""
        + category
        + "\",\"description\":\""
        + description
        + "\",\"form_started_at\":\""
        + started
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

    report(slug, body("NOT_A_CATEGORY", "Hay agua saliendo del tubo.", ""), "203.0.113.21")
        .andExpect(status().isBadRequest());
    report(slug, body("LEAK", "corto", ""), "203.0.113.22").andExpect(status().isBadRequest());
    report(slug, body("LEAK", "Hay agua saliendo\u202e del tubo.", ""), "203.0.113.23")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.details.fields[0].code").value("FIELD_CHARACTERS_NOT_ALLOWED"));
    report(
            slug,
            body(
                "LEAK",
                "Hay agua saliendo del tubo.",
                ",\"sector_id\":\"" + UUID.randomUUID() + "\""),
            "203.0.113.24")
        .andExpect(status().isBadRequest());
    report(slug, body("LEAK", "Hay agua saliendo del tubo.", ",\"name\":\"Ana\""), "203.0.113.25")
        .andExpect(status().isBadRequest());
    report("no-existe", body("LEAK", "Hay agua saliendo del tubo.", ""), "203.0.113.26")
        .andExpect(status().isNotFound());
  }

  @Test
  void aFilledHiddenFieldLooksAcceptedButStoresNothing() throws Exception {
    UUID aqueduct = newAqueductWithRules();

    report(
            slugOf(aqueduct),
            body("LEAK", "Hay agua saliendo del tubo.", ",\"website\":\"http://spam.example\""),
            "203.0.113.40")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REPORTED"))
        .andExpect(jsonPath("$.tracking_code").isNotEmpty());

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.incidents WHERE aqueduct_id = ?",
                Integer.class,
                aqueduct))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE aqueduct_id = ? AND type ="
                    + " 'HONEYPOT_TRIGGERED'",
                Integer.class,
                aqueduct))
        .isEqualTo(1);
  }

  @Test
  void aFormSentTooFastOrWithoutItsStartIsRefused() throws Exception {
    String slug = slugOf(newAqueductWithRules());

    report(
            slug,
            body("LEAK", "Hay agua saliendo del tubo.", OffsetDateTime.now(), ""),
            "203.0.113.50")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("form_started_at"));
    report(
            slug,
            "{\"category_code\":\"LEAK\",\"description\":\"Hay agua saliendo del tubo.\"}",
            "203.0.113.51")
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAddressSendsAtMostThreeReportsPerHour() throws Exception {
    String slug = slugOf(newAqueductWithRules());
    String valid = body("LEAK", "Hay agua saliendo del tubo.", "");

    for (int i = 0; i < 3; i++) {
      report(slug, valid, "203.0.113.60").andExpect(status().isCreated());
    }
    report(slug, valid, "203.0.113.60")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"));
    report(slug, valid, "203.0.113.61").andExpect(status().isCreated());
  }

  @Test
  void theReporterFollowsTheReportWithItsCodeOnly() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    String answer =
        report(
                slugOf(aqueduct),
                body("LEAK", "Hay agua saliendo del tubo de la escuela.", ""),
                "203.0.113.70")
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(answer, "$.tracking_code");
    String typed = code.substring(0, 4).toLowerCase() + "-" + code.substring(4);

    String status =
        mvc.perform(get("/api/v1/public/damage-reports/" + typed))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPORTED"))
            .andExpect(jsonPath("$.status_label_es").value("Recibido"))
            .andExpect(jsonPath("$.category_label_es").value("Fuga"))
            .andExpect(jsonPath("$.reported_at").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(status).doesNotContain("escuela").doesNotContain("203.0.113");

    UUID incident =
        owner.queryForObject(
            "SELECT id FROM ops.incidents WHERE aqueduct_id = ?", UUID.class, aqueduct);
    owner.update("UPDATE ops.incidents SET status = 'VERIFYING' WHERE id = ?", incident);
    owner.update(
        "INSERT INTO ops.incident_status_history (aqueduct_id, incident_id, from_status,"
            + " to_status, changed_at) VALUES (?, ?, 'REPORTED', 'VERIFYING', now() + interval"
            + " '1 minute')",
        aqueduct,
        incident);
    mvc.perform(get("/api/v1/public/damage-reports/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status_label_es").value("En revisión"));

    mvc.perform(get("/api/v1/public/damage-reports/NOPE")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/public/damage-reports/" + TrackingCode.generate().value()))
        .andExpect(status().isNotFound());
  }
}
