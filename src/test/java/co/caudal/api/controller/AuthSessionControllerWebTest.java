package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.auth.LogoutCommand;
import co.caudal.application.command.CommandBus;
import co.caudal.application.command.Done;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthSessionController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class AuthSessionControllerWebTest {

  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final UUID USER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;

  private static org.springframework.test.web.servlet.request.RequestPostProcessor session() {
    return jwt()
        .jwt(
            j ->
                j.subject(USER.toString())
                    .claim("role", "OPERATOR")
                    .claim("aqueduct_id", AQUEDUCT.toString()));
  }

  @Test
  void closesTheSessionAndClearsTheCookie() throws Exception {
    when(commands.dispatch(any())).thenReturn(Done.DONE);

    var response =
        mvc.perform(post(LOGOUT).with(session()).cookie(new Cookie("caudal_rt", "the-secret")))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse();

    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("caudal_rt=;")
        .contains("Max-Age=0")
        .contains("HttpOnly")
        .contains("Path=/api/v1/auth");
    ArgumentCaptor<LogoutCommand> captor = ArgumentCaptor.forClass(LogoutCommand.class);
    verify(commands).dispatch(captor.capture());
    assertThat(captor.getValue().caller().userId()).isEqualTo(USER);
    assertThat(captor.getValue().caller().aqueductId()).isEqualTo(AQUEDUCT);
    assertThat(captor.getValue().refreshSecret()).isEqualTo("the-secret");
  }

  @Test
  void withoutCookieItStillAnswers204() throws Exception {
    when(commands.dispatch(any())).thenReturn(Done.DONE);

    mvc.perform(post(LOGOUT).with(session())).andExpect(status().isNoContent());

    ArgumentCaptor<LogoutCommand> captor = ArgumentCaptor.forClass(LogoutCommand.class);
    verify(commands).dispatch(captor.capture());
    assertThat(captor.getValue().refreshSecret()).isNull();
  }

  @Test
  void withoutAccessTokenItAnswers401() throws Exception {
    mvc.perform(
            post(LOGOUT)
                .cookie(new Cookie("caudal_rt", "the-secret"))
                .header("X-Requested-With", "caudal-web")
                .header("Origin", "https://app.example.test"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(commands);
  }

  @Test
  void aTokenWithoutAqueductAnswers401() throws Exception {
    mvc.perform(post(LOGOUT).with(jwt().jwt(j -> j.subject(USER.toString()))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(commands);
  }

  @Test
  void aRequestWithoutBearerNeedsTheCsrfHeadersAndAnswers403WithoutThem() throws Exception {
    mvc.perform(
            post(LOGOUT)
                .cookie(new Cookie("caudal_rt", "the-secret"))
                .header("Origin", "https://app.example.test"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(commands);
  }
}
