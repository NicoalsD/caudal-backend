package co.caudal.infrastructure.security;

import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers {@code 403 FORBIDDEN} in the canonical format and records {@code PERMISSION_DENIED} in
 * {@code iam.security_events}. A failure while recording never changes the answer.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiAccessDeniedHandler.class);

  private final SecurityErrorWriter errors;
  private final SecurityEventPort events;

  ApiAccessDeniedHandler(SecurityErrorWriter errors, SecurityEventPort events) {
    this.errors = errors;
    this.events = events;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    recordDenial(request);
    errors.write(request, response, ErrorCode.FORBIDDEN);
  }

  private void recordDenial(HttpServletRequest request) {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      UUID actor = null;
      UUID aqueduct = null;
      if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
        actor = parseUuid(jwt.getSubject());
        aqueduct = parseUuid(jwt.getClaimAsString(JwtAccessTokenIssuerAdapter.CLAIM_AQUEDUCT_ID));
      }
      events.record(
          new SecurityEvent(
              SecurityEventType.PERMISSION_DENIED,
              Severity.LOW,
              actor,
              aqueduct,
              null,
              Map.of("method", request.getMethod(), "path", request.getRequestURI())));
    } catch (RuntimeException failure) {
      LOG.warn("Could not record PERMISSION_DENIED: {}", failure.getClass().getSimpleName());
    }
  }

  private static UUID parseUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException notAUuid) {
      return null;
    }
  }
}
