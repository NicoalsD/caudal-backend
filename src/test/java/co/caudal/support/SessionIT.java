package co.caudal.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.user.NormalizedPassword;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests that sign in for real: accounts with a real Argon2id hash, a login through the
 * API from a fresh address (so the per address limits of one test never reach another) and helpers
 * for the cookie and the bearer header.
 */
public abstract class SessionIT extends AppRoleIT {

  /** Password of every account created by {@link #account}. */
  protected static final String PASSWORD = "clave-de-prueba-larga";

  /** An origin allowed by the test profile. */
  protected static final String ORIGIN = "https://app.example.test";

  private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);
  private static final String ADDRESS_PREFIX = "203.0.113.";
  private static final int ADDRESS_RANGE = 250;

  @Autowired protected MockMvc mvc;
  @Autowired protected PasswordHasherPort hasher;

  /** A signed-in session. */
  public record Session(UUID userId, UUID aqueductId, String accessToken, String refreshSecret) {

    /** Value of the {@code Authorization} header. */
    public String bearer() {
      return "Bearer " + accessToken;
    }

    /** The refresh cookie. */
    public Cookie cookie() {
      return new Cookie("caudal_rt", refreshSecret);
    }
  }

  /** An account with a member role in a new aqueduct. */
  public record Account(UUID userId, UUID aqueductId, String username) {}

  /** Creates an account with {@link #PASSWORD} and a membership in a new aqueduct. */
  protected Account account(String username, String role) {
    return account(username, role, newAqueduct());
  }

  /** Creates an account with {@link #PASSWORD} and a membership in the given aqueduct. */
  protected Account account(String username, String role, UUID aqueductId) {
    UUID user = newUser(username, hasher.hash(NormalizedPassword.of(PASSWORD)));
    newMembership(user, aqueductId, role);
    return new Account(user, aqueductId, username);
  }

  /** Signs in through the API with {@link #PASSWORD}. */
  protected Session login(Account account) throws Exception {
    return login(account, PASSWORD);
  }

  /** Signs in through the API with the given password. */
  protected Session login(Account account, String password) throws Exception {
    String address = ADDRESS_PREFIX + (NEXT_ADDRESS.getAndIncrement() % ADDRESS_RANGE + 1);
    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(
                        request -> {
                          request.setRemoteAddr(address);
                          return request;
                        })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + account.username()
                            + "\",\"password\":\""
                            + password
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String token =
        response.getContentAsString().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
    return new Session(account.userId(), account.aqueductId(), token, secretOf(response));
  }

  /** Reads the refresh secret from a {@code Set-Cookie} header. */
  protected static String secretOf(MockHttpServletResponse response) {
    String cookie = response.getHeader("Set-Cookie");
    return cookie.substring("caudal_rt=".length(), cookie.indexOf(';'));
  }

  /** A refresh request as the web app sends it. */
  protected static MockHttpServletRequestBuilder refreshWith(String secret) {
    return post("/api/v1/auth/refresh")
        .cookie(new Cookie("caudal_rt", secret))
        .header("X-Requested-With", "caudal-web")
        .header("Origin", ORIGIN);
  }
}
