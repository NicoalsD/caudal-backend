package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.auth.ChangePasswordCommand;
import co.caudal.application.command.CommandBus;
import co.caudal.application.command.Done;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.PasswordPolicyViolationException;
import co.caudal.domain.user.PasswordPolicyViolationException.Rule;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.support.WebSecurityTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = PasswordController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class PasswordControllerChangeWebTest {

  private static final String CHANGE = "/api/v1/auth/change-password";
  private static final UUID USER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final String BODY =
      "{\"current_password\":\"clave-actual-larga\",\"new_password\":\"clave-nueva-bien-larga\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;

  private static RequestPostProcessor session() {
    return jwt()
        .jwt(
            j ->
                j.subject(USER.toString())
                    .claim("role", "OPERATOR")
                    .claim("aqueduct_id", AQUEDUCT.toString()));
  }

  @Test
  void changesThePasswordAndClearsTheRefreshCookie() throws Exception {
    when(commands.dispatch(any())).thenReturn(Done.DONE);

    var response =
        mvc.perform(
                post(CHANGE).with(session()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    assertThat(response.getContentAsString()).isEmpty();
    ArgumentCaptor<ChangePasswordCommand> captor =
        ArgumentCaptor.forClass(ChangePasswordCommand.class);
    verify(commands).dispatch(captor.capture());
    assertThat(captor.getValue().caller().userId()).isEqualTo(USER);
    assertThat(captor.getValue().currentPassword()).isEqualTo("clave-actual-larga");
    assertThat(captor.getValue().newPassword()).isEqualTo("clave-nueva-bien-larga");
  }

  @Test
  void withoutAccessTokenAnswers401() throws Exception {
    mvc.perform(post(CHANGE).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(commands);
  }

  @Test
  void aMissingUnknownOrOversizedFieldAnswers400() throws Exception {
    String tooLong = "a".repeat(129);
    for (String body :
        new String[] {
          "{\"current_password\":\"x\"}",
          "{\"new_password\":\"clave-nueva-bien-larga\"}",
          "{\"current_password\":\"\",\"new_password\":\"clave-nueva-bien-larga\"}",
          "{\"current_password\":\"x\",\"new_password\":\"" + tooLong + "\"}",
          "{\"current_password\":\"x\",\"new_password\":\"y\",\"role\":\"BOARD_ADMIN\"}",
          "{\"current_password\":1,\"new_password\":\"y\"}",
          "{"
        }) {
      mvc.perform(
              post(CHANGE).with(session()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    verifyNoInteractions(commands);
  }

  @Test
  void aWrongCurrentPasswordAnswers401() throws Exception {
    when(commands.dispatch(any())).thenThrow(new UnauthorizedException());

    mvc.perform(post(CHANGE).with(session()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void aWeakNewPasswordAnswers422WithoutEchoingIt() throws Exception {
    when(commands.dispatch(any())).thenThrow(new PasswordPolicyViolationException(Rule.COMMON));

    var response =
        mvc.perform(
                post(CHANGE).with(session()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.details.rule").value("COMMON"))
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString()).doesNotContain("clave-nueva-bien-larga");
  }
}
