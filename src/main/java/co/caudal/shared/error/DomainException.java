package co.caudal.shared.error;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * Base class of every business exception.
 *
 * <p>It carries an {@link ErrorCode}, optional arguments for the Spanish message template and
 * optional details for the client. Details never contain secrets, stack traces or SQL.
 */
public class DomainException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient Map<String, Object> details;
  private final transient Object[] messageArguments;

  /**
   * Creates an exception without details.
   *
   * @param code error code of the catalog
   */
  public DomainException(ErrorCode code) {
    this(code, Map.of());
  }

  /**
   * Creates an exception with details and message arguments.
   *
   * @param code error code of the catalog
   * @param details safe details for the client
   * @param messageArguments values for the placeholders of the message template
   */
  public DomainException(ErrorCode code, Map<String, Object> details, Object... messageArguments) {
    super(Objects.requireNonNull(code, "code").name());
    this.code = code;
    this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
    this.messageArguments = messageArguments.clone();
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }

  public Object[] messageArguments() {
    return messageArguments.clone();
  }
}
