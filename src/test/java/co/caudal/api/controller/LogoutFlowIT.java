package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Closing a session end to end, as the least-privilege role. */
class LogoutFlowIT extends SessionIT {

  @Test
  void logoutRevokesTheFamilyOfTheCookieAndTheTokenCannotRefreshAnymore() throws Exception {
    Account account = account("logout.one", "OPERATOR");
    Session session = login(account);

    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/auth/logout")
                    .header("Authorization", session.bearer())
                    .cookie(session.cookie()))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    assertThat(
            owner.queryForObject(
                "SELECT revoked_reason FROM iam.refresh_tokens WHERE user_id = ?",
                String.class,
                account.userId()))
        .isEqualTo("LOGOUT");
    mvc.perform(refreshWith(session.refreshSecret()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
  }

  @Test
  void logoutKeepsTheOtherDeviceOpenAndTheAccessTokenValid() throws Exception {
    Account account = account("logout.two", "OPERATOR");
    Session phone = login(account);
    Session laptop = login(account);

    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", phone.bearer())
                .cookie(phone.cookie()))
        .andExpect(status().isNoContent());

    mvc.perform(refreshWith(laptop.refreshSecret())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", phone.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void aCookieOfAnotherAccountClosesNothing() throws Exception {
    Session mine = login(account("logout.mine", "OPERATOR"));
    Session theirs = login(account("logout.theirs", "OPERATOR"));

    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", mine.bearer())
                .cookie(theirs.cookie()))
        .andExpect(status().isNoContent());

    mvc.perform(refreshWith(theirs.refreshSecret())).andExpect(status().isOk());
  }

  @Test
  void withoutAnAccessTokenTheAnswerIs401() throws Exception {
    Session session = login(account("logout.anon", "OPERATOR"));

    mvc.perform(
            post("/api/v1/auth/logout")
                .header("X-Requested-With", "caudal-web")
                .header("Origin", ORIGIN)
                .cookie(session.cookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutAllRevokesEverySessionAndTheOtherDevicesTokenStopsWorking() throws Exception {
    Account account = account("logout.all", "OPERATOR");
    Session phone = login(account);
    Session laptop = login(account);

    mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", phone.bearer()))
        .andExpect(status().isNoContent());

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.refresh_tokens WHERE user_id = ?"
                    + " AND revoked_reason = 'LOGOUT_ALL'",
                Integer.class,
                account.userId()))
        .isEqualTo(2);
    assertThat(
            owner.queryForObject(
                "SELECT token_version FROM iam.users WHERE id = ?",
                Integer.class,
                account.userId()))
        .isEqualTo(1);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", laptop.bearer()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    mvc.perform(refreshWith(laptop.refreshSecret())).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutAllDoesNotAffectAnotherAccount() throws Exception {
    Session mine = login(account("logout.all.mine", "OPERATOR"));
    Session other = login(account("logout.all.other", "OPERATOR"));

    mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", mine.bearer()))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").header("Authorization", other.bearer()))
        .andExpect(status().isOk());
  }
}
