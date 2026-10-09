package co.caudal.api.error;

import co.caudal.api.filter.RequestIdFilter;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into the canonical error body (docs/API.md, section 1.3).
 *
 * <p>Only {@link DomainException} and {@link ErrorCode} are known here. Messages come from {@code
 * messages_es.properties}; responses never include stack traces, SQL or the values sent.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  /** Locale of every user facing message. */
  static final Locale MESSAGES_LOCALE = Locale.forLanguageTag("es");

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");
  private static final Set<String> FORMAT_CONSTRAINTS = Set.of("Pattern", "Email", "Digits");
  private static final Set<String> RANGE_CONSTRAINTS =
      Set.of(
          "Min",
          "Max",
          "DecimalMin",
          "DecimalMax",
          "Positive",
          "PositiveOrZero",
          "Negative",
          "NegativeOrZero",
          "Past",
          "PastOrPresent",
          "Future",
          "FutureOrPresent");

  private final MessageSource messages;

  public ApiExceptionHandler(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
    return build(ex.code(), ex.details(), ex.messageArguments(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleInvalidBody(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<Map<String, String>> fields = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fields.add(fieldDetail(error.getField(), error.getCode(), error.getRejectedValue()));
    }
    return build(ErrorCode.VALIDATION_ERROR, Map.of("fields", fields), new Object[0], request);
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
    LOG.debug("Rejected request input: {}", ex.getClass().getSimpleName());
    return build(ErrorCode.VALIDATION_ERROR, Map.of(), new Object[0], request);
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
    return build(ErrorCode.NOT_FOUND, Map.of(), new Object[0], request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, Map.of(), new Object[0], request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> handleTooLarge(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return build(ErrorCode.PAYLOAD_TOO_LARGE, Map.of(), new Object[0], request);
  }

  /**
   * Security failures raised inside a controller (a denied {@code @PreAuthorize}) are not business
   * errors: they go back to the security filters, which answer 401 or 403 in the canonical format
   * and record the event.
   */
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  void handleSecurity(RuntimeException ex) {
    throw ex;
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    LOG.error("Unexpected error while processing the request", ex);
    return build(ErrorCode.INTERNAL_ERROR, Map.of(), new Object[0], request);
  }

  private ResponseEntity<ErrorResponse> build(
      ErrorCode code, Map<String, Object> details, Object[] arguments, HttpServletRequest request) {
    String message = messages.getMessage(code.messageKey(), arguments, MESSAGES_LOCALE);
    ErrorResponse body = ErrorResponse.of(code.name(), message, details, requestId(request));
    return ResponseEntity.status(ErrorStatusMapping.statusOf(code)).body(body);
  }

  private static String requestId(HttpServletRequest request) {
    Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
    if (attribute instanceof String id) {
      return id;
    }
    String fromMdc = MDC.get(RequestIdFilter.MDC_KEY);
    return fromMdc != null ? fromMdc : UUID.randomUUID().toString();
  }

  private static Map<String, String> fieldDetail(
      String field, String constraint, Object rejectedValue) {
    return Map.of(
        "field", JsonFieldNames.toSnakeCase(field),
        "code", fieldCode(constraint, rejectedValue).name());
  }

  private static FieldErrorCode fieldCode(String constraint, Object rejectedValue) {
    if (constraint == null) {
      return FieldErrorCode.FIELD_FORMAT_INVALID;
    }
    if (REQUIRED_CONSTRAINTS.contains(constraint)) {
      return FieldErrorCode.FIELD_REQUIRED;
    }
    if ("Size".equals(constraint) || "Length".equals(constraint)) {
      return isShort(rejectedValue)
          ? FieldErrorCode.FIELD_TOO_SHORT
          : FieldErrorCode.FIELD_TOO_LONG;
    }
    if (FORMAT_CONSTRAINTS.contains(constraint)) {
      return FieldErrorCode.FIELD_FORMAT_INVALID;
    }
    if (RANGE_CONSTRAINTS.contains(constraint)) {
      return FieldErrorCode.FIELD_OUT_OF_RANGE;
    }
    return FieldErrorCode.FIELD_FORMAT_INVALID;
  }

  private static boolean isShort(Object rejectedValue) {
    return rejectedValue == null
        || (rejectedValue instanceof CharSequence text && text.isEmpty())
        || (rejectedValue instanceof java.util.Collection<?> items && items.isEmpty());
  }
}
