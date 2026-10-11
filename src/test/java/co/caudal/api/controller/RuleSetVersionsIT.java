package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
}
