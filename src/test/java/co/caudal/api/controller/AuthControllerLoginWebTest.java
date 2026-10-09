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
import co.caudal.application.auth.LoginInput;
import co.caudal.application.auth.LoginResult;
import co.caudal.application.auth.LoginUseCase;
import co.caudal.domain.user.InvalidCredentialsException;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.WebSecurityTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class AuthControllerLoginWebTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String BODY =
      "{\"username\":\"test.operator\",\"password\":\"clave-de-prueba-larga\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private LoginUseCase login;
  @MockitoBean private GetCurrentUserUseCase currentUser;

  private static LoginResult result() {
    return new LoginResult(
        new IssuedAccessToken("jwt-value", Duration.ofMinutes(15)),
        new IssuedRefreshToken(
            "refresh-secret-value", Instant.parse("2026-10-16T13:00:00Z"), Duration.ofDays(7)),
        new LoginResult.SessionUser(
            UUID.fromString("0190f3a2-0000-7000-8000-000000000001"),
            "test.operator",
            "Persona de Prueba Uno",
            "OPERATOR",
            UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa"),
            false));
  }

  @Test
  void signsInWithoutAnyAuthenticationHeader() throws Exception {
    when(login.execute(any())).thenReturn(result());

    var response =
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.access_token").value("jwt-value"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andExpect(jsonPath("$.user.full_name").value("Persona de Prueba Uno"))
            .andExpect(jsonPath("$.user.role").value("OPERATOR"))
            .andExpect(jsonPath("$.user.must_change_password").value(false))
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString()).doesNotContain("refresh-secret-value");
    String cookie = response.getHeader("Set-Cookie");
    assertThat(cookie)
        .startsWith("caudal_rt=refresh-secret-value")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=None")
        .contains("Path=/api/v1/auth")
        .contains("Max-Age=604800");
  }

  @Test
  void passesTheCallerDataToTheUseCase() throws Exception {
    when(login.execute(any())).thenReturn(result());

    mvc.perform(
            post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "JUnit agent")
                .content(BODY))
        .andExpect(status().isOk());

    ArgumentCaptor<LoginInput> captor = ArgumentCaptor.forClass(LoginInput.class);
    verify(login).execute(captor.capture());
    assertThat(captor.getValue().username()).isEqualTo("test.operator");
    assertThat(captor.getValue().userAgent()).isEqualTo("JUnit agent");
    assertThat(captor.getValue().ipAddress()).isEqualTo("127.0.0.1");
  }

  @Test
  void aFailedLoginAnswers401WithTheGenericMessageAndNoCookie() throws Exception {
    when(login.execute(any())).thenThrow(new InvalidCredentialsException());

    mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Set-Cookie"))
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error.message").value("Usuario o contraseña incorrectos."));
  }

  @Test
  void rateLimitedAnswers429() throws Exception {
    when(login.execute(any())).thenThrow(new DomainException(ErrorCode.RATE_LIMITED));

    mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
  }

  @Test
  void missingFieldsAreValidationErrors() throws Exception {
    mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='username')].code").value("FIELD_REQUIRED"))
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='password')].code")
                .value("FIELD_REQUIRED"));
    verifyNoInteractions(login);
  }

  @Test
  void emptyAndOverlongValuesAreValidationErrors() throws Exception {
    String tooLong = "a".repeat(129);
    mvc.perform(
            post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='username')].code")
                .value("FIELD_TOO_SHORT"))
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='password')].code")
                .value("FIELD_TOO_LONG"));
    verifyNoInteractions(login);
  }

  @Test
  void unknownFieldsAreRejected() throws Exception {
    mvc.perform(
            post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"test.operator\",\"password\":\"x\",\"role\":\"BOARD_ADMIN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(login);
  }

  @Test
  void aNonTextPasswordIsNotCoerced() throws Exception {
    mvc.perform(
            post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test.operator\",\"password\":123456789012}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(login);
  }

  @Test
  void onlyJsonIsAccepted() throws Exception {
    mvc.perform(post(LOGIN).contentType(MediaType.TEXT_PLAIN).content(BODY))
        .andExpect(status().isUnsupportedMediaType());
  }
}
