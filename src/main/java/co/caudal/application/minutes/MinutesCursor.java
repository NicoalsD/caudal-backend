package co.caudal.application.minutes;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cursor of the list of minutes (docs/API.md, section 1.5): the moment the minutes were generated
 * and the id of the last minutes of a page, signed by {@link SignedCursorCodec}.
 */
public class MinutesCursor {

  /**
   * A position in the list.
   *
   * @param generatedAt the moment the minutes were generated
   * @param id the id of the last minutes of the page
   */
  public record Position(Instant generatedAt, UUID id) {}

  private static final String LIST = "minutes";
  private static final char BAR = '|';

  private final SignedCursorCodec codec;

  /**
   * Creates the cursor codec of minutes.
   *
   * @param codec signs and verifies the position
   */
  public MinutesCursor(SignedCursorCodec codec) {
    this.codec = codec;
  }

  /**
   * Builds the cursor that follows a minutes. The moment is cut to microseconds, the precision of
   * the database, so that the next page neither skips nor repeats rows.
   *
   * @param generatedAt when the minutes were generated
   * @param id id of the minutes
   * @return the opaque text
   */
  public String encode(Instant generatedAt, UUID id) {
    return codec.encode(LIST, generatedAt.truncatedTo(ChronoUnit.MICROS) + "|" + id);
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
          Instant.parse(position.substring(0, bar)), UUID.fromString(position.substring(bar + 1)));
    } catch (DateTimeParseException | IllegalArgumentException | IndexOutOfBoundsException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
