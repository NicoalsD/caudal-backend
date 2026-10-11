package co.caudal.shared.error;

import java.io.IOException;
import java.io.Serial;

/**
 * The body of a request without a declared length went over its limit while it was read. The API
 * answers {@code 413 PAYLOAD_TOO_LARGE}.
 */
public class BodyTooLargeException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public BodyTooLargeException() {
    super("Request body over its limit");
  }
}
