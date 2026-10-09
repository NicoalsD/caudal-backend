package co.caudal.infrastructure.security;

import co.caudal.api.error.ErrorResponse;
import co.caudal.api.error.ErrorStatusMapping;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the canonical error body for failures that happen in the security filters, before any
 * controller or {@code @RestControllerAdvice} can run (missing or invalid token, forbidden,
 * oversized header).
 */
@Component
public class SecurityErrorWriter {

  private final MessageSource messages;
  private final JsonMapper json;

  SecurityErrorWriter(MessageSource messages, JsonMapper json) {
    this.messages = messages;
    this.json = json;
  }

  /**
   * Writes the error response.
   *
   * @param request the failed request
   * @param response the response to fill
   * @param code the error code; its status and Spanish message come from the catalog
   * @throws IOException if the body cannot be written
   */
  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    String message = messages.getMessage(code.messageKey(), new Object[0], MessagesConfig.LOCALE);
    ErrorResponse body = ErrorResponse.of(code.name(), message, Map.of(), requestId(request));
    response.setStatus(ErrorStatusMapping.statusOf(code).value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(json.writeValueAsString(body));
  }

  private static String requestId(HttpServletRequest request) {
    Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
    if (attribute instanceof String id) {
      return id;
    }
    String fromMdc = MDC.get(RequestIdFilter.MDC_KEY);
    return fromMdc != null ? fromMdc : UUID.randomUUID().toString();
  }
}
