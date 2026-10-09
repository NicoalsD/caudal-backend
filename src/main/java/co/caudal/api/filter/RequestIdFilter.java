package co.caudal.api.filter;

import co.caudal.shared.FieldLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request.
 *
 * <p>A valid {@code X-Request-Id} sent by the client is reused; anything else is replaced by a new
 * UUID. The id is exposed in the {@code X-Request-Id} response header and stored in the MDC as
 * {@code request_id}, so every JSON log line and every error body carries the same value.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  /** Header used to receive and return the correlation id. */
  public static final String HEADER = "X-Request-Id";

  /** MDC key, in snake_case like the rest of the JSON contract. */
  public static final String MDC_KEY = "request_id";

  /** Request attribute holding the id for code that has no access to the MDC. */
  public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

  private static final Pattern VALID_ID = Pattern.compile(FieldLimits.REQUEST_ID_PATTERN);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolve(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, requestId);
    response.setHeader(HEADER, requestId);
    MDC.put(MDC_KEY, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  static String resolve(String candidate) {
    if (candidate != null && VALID_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }
}
