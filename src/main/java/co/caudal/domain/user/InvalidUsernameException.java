package co.caudal.domain.user;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;
import java.util.List;
import java.util.Map;

/** The text is not a valid username. The response never echoes the rejected value. */
public class InvalidUsernameException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception, reported as a format error on the {@code username} field. */
  public InvalidUsernameException() {
    super(
        ErrorCode.VALIDATION_ERROR,
        Map.of("fields", List.of(Map.of("field", "username", "code", "FIELD_FORMAT_INVALID"))));
  }
}
