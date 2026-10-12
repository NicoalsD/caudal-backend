package co.caudal.application.audit;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Cursor of the security events (docs/API.md, section 1.5): the moment and the id of the last event
 * of a page, signed by {@link SignedCursorCodec}.
 */
public class SecurityEventCursor {

  /**
   * A position in the list.
   *
   * @param createdAt the moment of the last event of the page
   * @param id the id of the last event of the page
   */
  public record Position(Instant createdAt, long id) {}

  private static final String LIST = "security-event";
  private static final char BAR = '|';

  private final SignedCursorCodec codec;

  /**
   * Creates the cursor codec of the security events.
   *
   * @param codec signs and verifies the position
   */
  public SecurityEventCursor(SignedCursorCodec codec) {
    this.codec = codec;
  }

  /**
   * Builds the cursor that follows an event, cut to microseconds like the database.
   *
   * @param createdAt when the event was created
   * @param id id of the event
   * @return the opaque text
   */
  public String encode(Instant createdAt, long id) {
    return codec.encode(LIST, createdAt.truncatedTo(ChronoUnit.MICROS) + "|" + id);
  }

  /**
   * Reads and verifies a cursor.
   *
   * @param cursor the text from the client
   * @return the position
   * @throws DomainException with {@code CURSOR_INVALID} if it is malformed or altered
   */
  public Position decode(String cursor) {
    String position = codec.decode(LIST, cursor);
    int bar = position.indexOf(BAR);
    try {
      return new Position(
          Instant.parse(position.substring(0, bar)), Long.parseLong(position.substring(bar + 1)));
    } catch (DateTimeParseException | NumberFormatException | IndexOutOfBoundsException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
