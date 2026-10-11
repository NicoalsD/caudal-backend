package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
