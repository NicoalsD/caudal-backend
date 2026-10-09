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
import co.caudal.application.command.CommandBus;
import co.caudal.application.user.CreateUserCommand;
import co.caudal.application.user.CreatedUser;
import co.caudal.domain.user.InvalidUsernameException;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.WebSecurityTestConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = UserController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class UserControllerCreateWebTest {

  private static final String USERS = "/api/v1/users";
  private static final UUID ADMIN = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final String BODY =
      "{\"username\":\"operador.ejemplo\",\"full_name\":\"Persona de Ejemplo\","
          + "\"aqueduct_id\":\"0190f3a2-0000-7000-8000-0000000000aa\",\"role\":\"OPERATOR\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;

  private static RequestPostProcessor admin() {
    return jwt()
        .jwt(
            j ->
                j.subject(ADMIN.toString())
                    .claim("role", "BOARD_ADMIN")
                    .claim("aqueduct_id", AQUEDUCT.toString()))
        .authorities(new SimpleGrantedAuthority("USER_MANAGE"));
  }

  private static CreatedUser created() {
    return new CreatedUser(
        UUID.fromString("0190f3a2-0000-7000-8000-000000000002"),
        "operador.ejemplo",
        "Persona de Ejemplo",
        "ACTIVE",
        true,
        UUID.fromString("0190f3a2-0000-7000-8000-000000000003"),
        AQUEDUCT,
        "OPERATOR",
        "ABCD-EFGH-JKMN",
        Instant.parse("2026-10-10T13:00:00Z"));
  }

  @Test
  void createsTheUserAndShowsTheActivationCodeOnce() throws Exception {
    when(commands.dispatch(any())).thenReturn(created());

    mvc.perform(post(USERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.username").value("operador.ejemplo"))
        .andExpect(jsonPath("$.must_change_password").value(true))
        .andExpect(jsonPath("$.activation_code").value("ABCD-EFGH-JKMN"))
        .andExpect(jsonPath("$.expires_at").value("2026-10-10T13:00:00Z"))
        .andExpect(jsonPath("$.membership.role").value("OPERATOR"))
        .andExpect(jsonPath("$.password_hash").doesNotExist());

    ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
    verify(commands).dispatch(captor.capture());
    assertThat(captor.getValue().caller().userId()).isEqualTo(ADMIN);
    assertThat(captor.getValue().caller().aqueductId()).isEqualTo(AQUEDUCT);
    assertThat(captor.getValue().aqueductId()).isEqualTo(AQUEDUCT);
    assertThat(captor.getValue().role()).isEqualTo("OPERATOR");
  }

  @Test
  void withoutAccessTokenAnswers401() throws Exception {
    mvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(commands);
  }

  @Test
  void withoutThePermissionAnswers403() throws Exception {
    mvc.perform(
            post(USERS)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(ADMIN.toString())
                                    .claim("role", "BOARD_MEMBER")
                                    .claim("aqueduct_id", AQUEDUCT.toString()))
                        .authorities(new SimpleGrantedAuthority("RULESET_VIEW")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(commands);
  }

  @Test
  void invalidInputAnswers400WithTheFieldAndNeverEchoesTheValue() throws Exception {
    String[] bodies = {
      "{}",
      BODY.replace("operador.ejemplo", "ab"),
      BODY.replace("operador.ejemplo", "a".repeat(33)),
      BODY.replace("Persona de Ejemplo", "a".repeat(161)),
      BODY.replace("0190f3a2-0000-7000-8000-0000000000aa", "not-a-uuid"),
      BODY.replace("OPERATOR", "operator"),
      BODY.replace("OPERATOR", "X"),
      BODY.replace("}", ",\"must_change_password\":false}"),
      BODY.replace("\"role\":\"OPERATOR\"", "\"role\":7"),
      "[]"
    };
    for (String body : bodies) {
      mvc.perform(post(USERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    verifyNoInteractions(commands);
  }

  @Test
  void aFieldErrorFoundByTheDomainKeepsItsFieldAndCode() throws Exception {
    when(commands.dispatch(any())).thenThrow(new InvalidUsernameException());

    mvc.perform(
            post(USERS)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("operador.ejemplo", "Not A User!")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("username"))
        .andExpect(jsonPath("$.error.details.fields[0].code").value("FIELD_FORMAT_INVALID"));
  }

  @Test
  void aTakenUsernameAnswers409() throws Exception {
    when(commands.dispatch(any())).thenThrow(new DomainException(ErrorCode.CONFLICT));

    mvc.perform(post(USERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void anAqueductOfAnotherSessionAnswers403() throws Exception {
    when(commands.dispatch(any())).thenThrow(new DomainException(ErrorCode.FORBIDDEN));

    mvc.perform(post(USERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
