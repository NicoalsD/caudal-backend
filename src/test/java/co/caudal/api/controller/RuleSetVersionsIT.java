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

/** Versions of the Board's rules end to end. */
class RuleSetVersionsIT extends SessionIT {

  private ResultActions call(Session session, String path) throws Exception {
    return mvc.perform(get("/api/v1/rule-sets" + path).header("Authorization", session.bearer()));
  }

  @Test
  void listsTheVersionsOfTheAqueductNewestFirst() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.list", "BOARD_MEMBER", aqueduct));

    call(session, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].version").value(1))
        .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
    call(session, "?status=DRAFT").andExpect(jsonPath("$.items.length()").value(0));
    call(session, "?status=OLD").andExpect(status().isBadRequest());
    call(session, "?cursor=forged").andExpect(status().isBadRequest());
  }

  @Test
  void aRoleWithoutRulesetViewIsForbidden() throws Exception {
    Session session = login(account("rules.support", "SUPPORT_ENTITY", newAqueductWithRules()));

    call(session, "").andExpect(status().isForbidden());
  }

  @Test
  void showsAVersionWithItsHistoryAndHidesOtherAqueducts() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.detail", "OPERATOR", aqueduct));
    UUID id =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ?", UUID.class, aqueduct);
    UUID foreign =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ?",
            UUID.class,
            newAqueductWithRules());

    call(session, "/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.change_reason").value("Versión inicial de prueba."))
        .andExpect(jsonPath("$.bands.length()").value(3))
        .andExpect(jsonPath("$.valve_order.length()").value(2))
        .andExpect(jsonPath("$.activated_by").isNotEmpty());
    call(session, "/" + foreign).andExpect(status().isNotFound());
    call(session, "/" + UUID.randomUUID()).andExpect(status().isNotFound());
  }

  private ResultActions createDraft(Session session, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/rule-sets")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void aDraftIsAFullCopyOfTheActiveVersionAndThereIsOnlyOne() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.draft", "BOARD_MEMBER", aqueduct));
    UUID active =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ?", UUID.class, aqueduct);

    createDraft(session, "{\"change_reason\":\"Sube la reserva por la temporada seca.\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.based_on_rule_set_id").value(active.toString()))
        .andExpect(jsonPath("$.reserve_level").value(1.00))
        .andExpect(jsonPath("$.bands.length()").value(3))
        .andExpect(jsonPath("$.sectors.length()").value(2))
        .andExpect(jsonPath("$.valve_order.length()").value(2))
        .andExpect(jsonPath("$.operating_windows.length()").value(1))
        .andExpect(jsonPath("$.valid_from").doesNotExist());
    createDraft(session, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.details.reason").value("DRAFT_EXISTS"));
    call(session, "/" + active).andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void aShortReasonIsInvalidAndAnOperatorCannotDraft() throws Exception {
    UUID aqueduct = newAqueductWithRules();

    createDraft(
            login(account("rules.short", "BOARD_ADMIN", aqueduct)), "{\"change_reason\":\"corto\"}")
        .andExpect(status().isBadRequest());
    createDraft(login(account("rules.operator", "OPERATOR", aqueduct)), "{}")
        .andExpect(status().isForbidden());
  }

  private ResultActions patchRules(Session session, UUID id, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/rule-sets/" + id)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private UUID draftOf(Session session) throws Exception {
    String body =
        createDraft(session, "{}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(body.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1"));
  }

  @Test
  void editsTheScalarsAndReplacesTheBandsOfTheDraft() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.patch", "BOARD_MEMBER", aqueduct));
    UUID draft = draftOf(session);

    patchRules(
            session,
            draft,
            "{\"reserve_level\":1.20,\"change_reason\":\"Sube la reserva por la sequía.\","
                + "\"bands\":[{\"band\":\"CRITICAL\",\"min_level\":0,\"max_level\":2,"
                + "\"daily_service_hours\":2,\"priority_only\":true},{\"band\":\"HIGH\","
                + "\"min_level\":2,\"max_level\":5,\"daily_service_hours\":12}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserve_level").value(1.20))
        .andExpect(jsonPath("$.change_reason").value("Sube la reserva por la sequía."))
        .andExpect(jsonPath("$.bands.length()").value(2))
        .andExpect(jsonPath("$.sectors.length()").value(2))
        .andExpect(jsonPath("$.max_shift_hours").value(4.00));
  }

  @Test
  void overlappingBandsAreInvalidAndTheDraftKeepsItsBands() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.overlap", "BOARD_ADMIN", aqueduct));
    UUID draft = draftOf(session);

    patchRules(
            session,
            draft,
            "{\"bands\":[{\"band\":\"LOW\",\"min_level\":0,\"max_level\":3.6,"
                + "\"daily_service_hours\":8},{\"band\":\"HIGH\",\"min_level\":3.5,"
                + "\"max_level\":5,\"daily_service_hours\":16}]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("RULE_SET_INVALID"))
        .andExpect(jsonPath("$.error.details.constraint").value("rule_level_bands_no_overlap"));
    call(session, "/" + draft).andExpect(jsonPath("$.bands.length()").value(3));
  }

  @Test
  void anActiveVersionIsImmutableAndBadValuesAreRejected() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.immutable", "BOARD_ADMIN", aqueduct));
    UUID active =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ?", UUID.class, aqueduct);

    patchRules(session, active, "{\"reserve_level\":2}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RULE_SET_IMMUTABLE"));
    UUID draft = draftOf(session);
    patchRules(session, draft, "{\"forecast_horizon_days\":9}").andExpect(status().isBadRequest());
    patchRules(session, draft, "{\"reserve_policy\":\"PANIC\"}").andExpect(status().isBadRequest());
    patchRules(session, draft, "{\"bands\":[]}").andExpect(status().isBadRequest());
    patchRules(
            session,
            draft,
            "{\"sectors\":[{\"sector_id\":\"" + UUID.randomUUID() + "\",\"is_included\":true}]}")
        .andExpect(status().isUnprocessableContent());
    patchRules(session, UUID.randomUUID(), "{}").andExpect(status().isNotFound());
  }

  private ResultActions activate(Session session, UUID id, String reason) throws Exception {
    return mvc.perform(
        post("/api/v1/rule-sets/" + id + "/activate")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(reason == null ? "{}" : "{\"change_reason\":\"" + reason + "\"}"));
  }

  @Test
  void activatingADraftSupersedesTheActiveVersion() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.activate", "BOARD_ADMIN", aqueduct));
    UUID old =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ?", UUID.class, aqueduct);
    UUID draft = draftOf(session);
    patchRules(session, draft, "{\"reserve_level\":1.20}").andExpect(status().isOk());

    activate(session, draft, "Se aprueba la reserva de 1,20 en la reunión.")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(
            jsonPath("$.change_reason").value("Se aprueba la reserva de 1,20 en la reunión."))
        .andExpect(jsonPath("$.valid_from").isNotEmpty())
        .andExpect(jsonPath("$.activated_by").value(session.userId().toString()));

    call(session, "/current")
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.reserve_level").value(1.20));
    call(session, "/" + old)
        .andExpect(jsonPath("$.status").value("SUPERSEDED"))
        .andExpect(jsonPath("$.valid_to").isNotEmpty());
    java.util.Map<String, Object> audit =
        owner.queryForMap(
            "SELECT action, entity_id, after_state->>'change_reason' AS reason,"
                + " (after_state->>'version')::int AS version FROM audit.audit_log"
                + " WHERE aqueduct_id = ? AND action = 'RULESET_ACTIVATED'",
            aqueduct);
    org.assertj.core.api.Assertions.assertThat(audit)
        .containsEntry("entity_id", draft.toString())
        .containsEntry("reason", "Se aprueba la reserva de 1,20 en la reunión.")
        .containsEntry("version", 2);
    activate(session, draft, "Otra vez la misma versión activa.")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void aGapOrAMissingReasonKeepsTheDraft() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("rules.gap", "BOARD_ADMIN", aqueduct));
    UUID draft = draftOf(session);
    patchRules(
            session,
            draft,
            "{\"bands\":[{\"band\":\"CRITICAL\",\"min_level\":0,\"max_level\":1.5,"
                + "\"daily_service_hours\":3,\"priority_only\":true},{\"band\":\"LOW\","
                + "\"min_level\":1.5,\"max_level\":3,\"daily_service_hours\":8},{\"band\":"
                + "\"HIGH\",\"min_level\":3.5,\"max_level\":5,\"daily_service_hours\":16}]}")
        .andExpect(status().isOk());

    activate(session, draft, null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    activate(session, draft, "Cambia las bandas de la temporada.")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("RULE_SET_INVALID"))
        .andExpect(jsonPath("$.error.details.violations[0].code").value("BAND_GAP"))
        .andExpect(jsonPath("$.error.details.violations[0].gaps[0].from").value("3.00"))
        .andExpect(jsonPath("$.error.details.violations[0].gaps[0].to").value("3.50"));
    call(session, "/" + draft).andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void onlyTheBoardAdministratorActivates() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("rules.member.act", "BOARD_MEMBER", aqueduct));

    activate(member, draftOf(member), "Intento de un miembro de la Junta.")
        .andExpect(status().isForbidden());
  }
}
