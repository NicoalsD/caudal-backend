package co.caudal.application.audit;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Cursor of the audit log (docs/API.md, section 1.5): the moment and the id of the last entry of a
 * page, signed by {@link SignedCursorCodec}.
 */
public class AuditLogCursor {

  /**
   * A position in the log.
   *
   * @param occurredAt the moment of the last entry of the page
   * @param id the id of the last entry of the page
   */
  public record Position(Instant occurredAt, long id) {}

  private static final String LIST = "audit-log";
  private static final char BAR = '|';

  private final SignedCursorCodec codec;

  /**
   * Creates the cursor codec of the audit log.
   *
   * @param codec signs and verifies the position
   */
  public AuditLogCursor(SignedCursorCodec codec) {
    this.codec = codec;
  }

  /**
   * Builds the cursor that follows an entry. The moment is cut to microseconds, the precision of
   * the database, so the next page neither skips nor repeats rows.
   *
   * @param occurredAt when the entry occurred
   * @param id id of the entry
   * @return the opaque text
   */
  public String encode(Instant occurredAt, long id) {
    return codec.encode(LIST, occurredAt.truncatedTo(ChronoUnit.MICROS) + "|" + id);
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
