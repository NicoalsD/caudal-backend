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
import co.caudal.application.auth.PasswordResetResult;
import co.caudal.application.auth.ResetPasswordCommand;
import co.caudal.application.command.CommandBus;
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

@WebMvcTest(controllers = PasswordController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class PasswordControllerResetWebTest {

  private static final String RESET = "/api/v1/auth/reset-password";
  private static final String BODY =
      "{\"username\":\"operador.ejemplo\",\"code\":\"ABCD-EFGH-JKMN\","
          + "\"new_password\":\"clave-nueva-bien-larga\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;

  @Test
  void isPublicAndSetsThePasswordWithoutAnySession() throws Exception {
    when(commands.dispatch(any()))
        .thenReturn(new PasswordResetResult(UUID.randomUUID(), UUID.randomUUID()));

    mvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", "no-store"));

    ArgumentCaptor<ResetPasswordCommand> captor =
        ArgumentCaptor.forClass(ResetPasswordCommand.class);
    verify(commands).dispatch(captor.capture());
    assertThat(captor.getValue().username()).isEqualTo("operador.ejemplo");
    assertThat(captor.getValue().code()).isEqualTo("ABCD-EFGH-JKMN");
    assertThat(captor.getValue().newPassword()).isEqualTo("clave-nueva-bien-larga");
  }

  @Test
  void aMissingUnknownOrOversizedFieldAnswers400() throws Exception {
    for (String body :
        new String[] {
          "{\"username\":\"a\",\"code\":\"b\"}",
          "{\"username\":\"a\",\"new_password\":\"b\"}",
          "{\"code\":\"b\",\"new_password\":\"b\"}",
          "{\"username\":\"\",\"code\":\"b\",\"new_password\":\"c\"}",
          "{\"username\":\"a\",\"code\":\"" + "A".repeat(33) + "\",\"new_password\":\"c\"}",
          "{\"username\":\"a\",\"code\":\"b\",\"new_password\":\"" + "p".repeat(129) + "\"}",
          "{\"username\":\"a\",\"code\":\"b\",\"new_password\":\"c\",\"role\":\"BOARD_ADMIN\"}",
          "{\"username\":\"a\",\"code\":1,\"new_password\":\"c\"}"
        }) {
      mvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    verifyNoInteractions(commands);
  }

  @Test
  void anInvalidCodeAnswers401WithTheGenericMessage() throws Exception {
    when(commands.dispatch(any())).thenThrow(new UnauthorizedException());

    mvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("Sesión no válida. Vuelve a iniciar sesión."));
  }

  @Test
  void aWeakPasswordAnswers422() throws Exception {
    when(commands.dispatch(any())).thenThrow(new PasswordPolicyViolationException(Rule.TOO_SHORT));

    mvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));
  }

  @Test
  void onlyPostIsAllowed() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(RESET))
        .andExpect(status().isNotFound());
  }
}
