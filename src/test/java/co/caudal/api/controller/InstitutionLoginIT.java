package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.support.AppRoleIT;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Two-step login by institution and the public directory, with the real database. */
class InstitutionLoginIT extends AppRoleIT {

  private static final String PASSWORD = "clave-de-prueba-larga";
  private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(100);

  @Autowired private MockMvc mvc;
  @Autowired private PasswordHasherPort hasher;

  private static RequestPostProcessor freshAddress() {
    String address = "198.51.100." + NEXT_ADDRESS.getAndIncrement();
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }

  /** An account whose institution is {@code aqueduct}, with an OPERATOR membership there. */
  private UUID accountIn(UUID aqueduct, String username) {
    UUID user = newUser(username, hasher.hash(NormalizedPassword.of(PASSWORD)));
    owner.update("UPDATE iam.users SET aqueduct_id = ? WHERE id = ?", aqueduct, user);
    newMembership(user, aqueduct, "OPERATOR");
    return user;
  }

  private String slugOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT slug FROM org.aqueducts WHERE id = ?", String.class, aqueduct);
  }

  private MockHttpServletResponse loginIn(String slug, String username, String password)
      throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .with(freshAddress())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"institution_slug\":\""
                        + slug
                        + "\",\"username\":\""
                        + username
                        + "\",\"password\":\""
                        + password
                        + "\"}"))
        .andReturn()
        .getResponse();
  }

  private static String withoutRequestId(MockHttpServletResponse response) throws Exception {
    return response.getContentAsString().replaceAll("\"request_id\":\"[^\"]*\"", "");
  }

  @Test
  void directoryAnswersWithoutLoginAndShowsOnlySlugAndName() throws Exception {
    UUID aqueduct = newAqueduct();
    String slug = slugOf(aqueduct);

    String body =
        mvc.perform(get("/api/v1/public/institutions").with(freshAddress()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].name").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("\"id\"")
        .doesNotContain("municipality")
        .doesNotContain("is_demo")
        .doesNotContain("password");
  }

  @Test
  void loginWithInstitutionSlugIssuesTheTokenForThatInstitution() throws Exception {
    UUID aqueduct = newAqueduct();
    accountIn(aqueduct, "slug.operator");

    MockHttpServletResponse response = loginIn(slugOf(aqueduct), "slug.operator", PASSWORD);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).contains(aqueduct.toString());
  }

  @Test
  void sameUsernameInTwoInstitutionsEntersEachOwnOne() throws Exception {
    UUID first = newAqueduct();
    UUID second = newAqueduct();
    accountIn(first, "shared.user");
    accountIn(second, "shared.user");

    MockHttpServletResponse inFirst = loginIn(slugOf(first), "shared.user", PASSWORD);
    MockHttpServletResponse inSecond = loginIn(slugOf(second), "shared.user", PASSWORD);

    assertThat(inFirst.getStatus()).isEqualTo(200);
    assertThat(inFirst.getContentAsString()).contains(first.toString());
    assertThat(inSecond.getStatus()).isEqualTo(200);
    assertThat(inSecond.getContentAsString()).contains(second.toString());
  }

  @Test
  void userOfAnotherInstitutionGetsTheSameGenericAnswerAsAnUnknownSlug() throws Exception {
    UUID own = newAqueduct();
    UUID other = newAqueduct();
    accountIn(own, "only.in.own");

    MockHttpServletResponse wrongInstitution = loginIn(slugOf(other), "only.in.own", PASSWORD);
    MockHttpServletResponse unknownSlug = loginIn("no-existe-aqui", "only.in.own", PASSWORD);

    assertThat(wrongInstitution.getStatus()).isEqualTo(401);
    assertThat(wrongInstitution.getContentAsString()).contains("INVALID_CREDENTIALS");
    assertThat(unknownSlug.getStatus()).isEqualTo(401);
    assertThat(withoutRequestId(wrongInstitution)).isEqualTo(withoutRequestId(unknownSlug));
  }

  @Test
  void wrongPasswordInAnInstitutionIsTheSameGenericAnswer() throws Exception {
    UUID aqueduct = newAqueduct();
    accountIn(aqueduct, "bad.password");

    MockHttpServletResponse wrong =
        loginIn(slugOf(aqueduct), "bad.password", "clave-equivocada-larga");
    MockHttpServletResponse unknownSlug =
        loginIn("no-existe-aqui", "bad.password", "clave-equivocada-larga");

    assertThat(wrong.getStatus()).isEqualTo(401);
    assertThat(withoutRequestId(wrong)).isEqualTo(withoutRequestId(unknownSlug));
  }

  @Test
  void withoutSlugADuplicatedUsernameDoesNotSignIn() throws Exception {
    accountIn(newAqueduct(), "dup.name");
    accountIn(newAqueduct(), "dup.name");

    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(freshAddress())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"dup.name\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(401);
  }
}
