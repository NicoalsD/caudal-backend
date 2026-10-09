package co.caudal.infrastructure.security;

import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Answers {@code 401 UNAUTHORIZED} in the canonical format when there is no valid token. */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String BEARER_CHALLENGE = "Bearer";

  private final SecurityErrorWriter errors;

  ApiAuthenticationEntryPoint(SecurityErrorWriter errors) {
    this.errors = errors;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
    errors.write(request, response, ErrorCode.UNAUTHORIZED);
  }
}
