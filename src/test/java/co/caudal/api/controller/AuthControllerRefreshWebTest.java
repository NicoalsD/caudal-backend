package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.application.auth.IssuedAccessToken;
import co.caudal.application.auth.IssuedRefreshToken;
import co.caudal.application.auth.LoginUseCase;
import co.caudal.application.auth.RefreshInput;
import co.caudal.application.auth.RefreshResult;
import co.caudal.application.auth.RefreshSessionUseCase;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.support.WebSecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = AuthController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class AuthControllerRefreshWebTest {

  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String ORIGIN = "https://app.example.test";

  @Autowired private MockMvc mvc;
  @MockitoBean private RefreshSessionUseCase refresh;
  @MockitoBean private LoginUseCase login;
  @MockitoBean private GetCurrentUserUseCase currentUser;

  private static MockHttpServletRequestBuilder validRequest() {
    return post(REFRESH)
        .cookie(new Cookie("caudal_rt", "old-secret"))
        .header("X-Requested-With", "caudal-web")
        .header("Origin", ORIGIN);
  }

  @Test
  void rotatesTheCookieAndReturnsANewAccessTokenWithoutAuthorizationHeader() throws Exception {
    when(refresh.execute(any()))
        .thenReturn(
            new RefreshResult(
                new IssuedAccessToken("new-jwt", Duration.ofMinutes(15)),
                new IssuedRefreshToken(
                    "new-secret", Instant.parse("2026-10-16T13:00:00Z"), Duration.ofDays(7))));

    var response =
        mvc.perform(validRequest())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.access_token").value("new-jwt"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString()).doesNotContain("new-secret");
    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("caudal_rt=new-secret")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=None")
        .contains("Path=/api/v1/auth");
    ArgumentCaptor<RefreshInput> captor = ArgumentCaptor.forClass(RefreshInput.class);
    verify(refresh).execute(captor.capture());
    assertThat(captor.getValue().refreshSecret()).isEqualTo("old-secret");
  }

  @Test
  void withoutTheClientHeaderAnswers403BeforeTouchingTheCookie() throws Exception {
    mvc.perform(
            post(REFRESH).cookie(new Cookie("caudal_rt", "old-secret")).header("Origin", ORIGIN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(refresh);
  }

  @Test
  void aForeignOriginAnswers403() throws Exception {
    mvc.perform(
            post(REFRESH)
                .cookie(new Cookie("caudal_rt", "old-secret"))
                .header("X-Requested-With", "caudal-web")
                .header("Origin", "https://evil.example.test"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(refresh);
  }

  @Test
  void aMissingOriginAnswers403() throws Exception {
    mvc.perform(
            post(REFRESH)
                .cookie(new Cookie("caudal_rt", "old-secret"))
                .header("X-Requested-With", "caudal-web"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(refresh);
  }

  @Test
  void withoutTheCookieAnswers401() throws Exception {
    when(refresh.execute(any())).thenThrow(new UnauthorizedException());

    mvc.perform(post(REFRESH).header("X-Requested-With", "caudal-web").header("Origin", ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    ArgumentCaptor<RefreshInput> captor = ArgumentCaptor.forClass(RefreshInput.class);
    verify(refresh).execute(captor.capture());
    assertThat(captor.getValue().refreshSecret()).isNull();
  }

  @Test
  void aReusedTokenAnswers401SessionRevoked() throws Exception {
    when(refresh.execute(any())).thenThrow(new SessionRevokedException());

    mvc.perform(validRequest())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"))
        .andExpect(
            jsonPath("$.error.message").value("Tu sesión se cerró. Vuelve a iniciar sesión."))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }
}
