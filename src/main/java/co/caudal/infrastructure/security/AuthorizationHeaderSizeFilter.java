package co.caudal.infrastructure.security;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects an {@code Authorization} header above 2 KiB before the token is parsed, so an oversized
 * value never reaches the JWT library (docs/Seguridad.md, section 2.3). It is created inside the
 * security configuration and is not a bean, so the servlet container does not register it twice.
 */
final class AuthorizationHeaderSizeFilter extends OncePerRequestFilter {

  private final SecurityErrorWriter errors;

  AuthorizationHeaderSizeFilter(SecurityErrorWriter errors) {
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null
        && header.getBytes(StandardCharsets.UTF_8).length > FieldLimits.AUTH_HEADER_MAX_BYTES) {
      errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
      return;
    }
    chain.doFilter(request, response);
  }
}
