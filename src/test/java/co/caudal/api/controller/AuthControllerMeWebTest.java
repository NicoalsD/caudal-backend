package co.caudal.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.auth.CurrentUser;
import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.application.auth.MembershipSummary;
import co.caudal.application.auth.UserProfile;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.support.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class AuthControllerMeWebTest {

  private static final UUID USER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");

  @Autowired private MockMvc mvc;
  @MockitoBean private GetCurrentUserUseCase currentUser;
  @MockitoBean private co.caudal.application.auth.LoginUseCase login;

  @Test
  void withoutTokenAnswers401InTheCanonicalFormat() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(currentUser);
  }

  @Test
  void returnsTheSessionInSnakeCase() throws Exception {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    when(currentUser.execute(USER, "OPERATOR"))
        .thenReturn(
            new CurrentUser(
                new UserProfile(USER, "test.operator", "Persona de Prueba Uno", "ACTIVE", false, 1),
                List.of(new MembershipSummary(AQUEDUCT, "OPERATOR", from, null)),
                List.of("DAY_CLOSE", "READING_CREATE")));

    mvc.perform(
            get("/api/v1/auth/me")
                .with(jwt().jwt(j -> j.subject(USER.toString()).claim("role", "OPERATOR"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(USER.toString()))
        .andExpect(jsonPath("$.username").value("test.operator"))
        .andExpect(jsonPath("$.full_name").value("Persona de Prueba Uno"))
        .andExpect(jsonPath("$.must_change_password").value(false))
        .andExpect(jsonPath("$.privacy_accepted_version").value(1))
        .andExpect(jsonPath("$.memberships[0].aqueduct_id").value(AQUEDUCT.toString()))
        .andExpect(jsonPath("$.memberships[0].role").value("OPERATOR"))
        .andExpect(jsonPath("$.memberships[0].valid_to").doesNotExist())
        .andExpect(jsonPath("$.permissions[0]").value("DAY_CLOSE"))
        .andExpect(jsonPath("$.password_hash").doesNotExist());
  }

  @Test
  void aTokenWhoseSubjectIsNotAnAccountIdAnswers401() throws Exception {
    mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject("not-a-uuid"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void anAccountThatNoLongerExistsAnswers401() throws Exception {
    when(currentUser.execute(any(), any())).thenThrow(new UnauthorizedException());

    mvc.perform(
            get("/api/v1/auth/me")
                .with(jwt().jwt(j -> j.subject(USER.toString()).claim("role", "OPERATOR"))))
        .andExpect(status().isUnauthorized());
    verify(currentUser).execute(USER, "OPERATOR");
  }

  @Test
  void onlyGetIsAllowed() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/auth/me")
                .with(jwt()))
        .andExpect(status().isNotFound());
  }
}
