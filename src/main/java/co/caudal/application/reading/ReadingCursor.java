package co.caudal.application.reading;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cursor of the list of readings (docs/API.md, section 1.5): the observation moment and the id of
 * the last reading of a page, signed by {@link SignedCursorCodec}.
 */
public class ReadingCursor {

  /**
   * A position in the list.
   *
   * @param observedAt the moment of the last reading of the page
   * @param id the id of the last reading of the page
   */
  public record Position(Instant observedAt, UUID id) {}

  private static final String LIST = "reading";
  private static final char BAR = '|';

  private final SignedCursorCodec codec;

  /**
   * Creates the cursor codec of readings.
   *
   * @param codec signs and verifies the position
   */
  public ReadingCursor(SignedCursorCodec codec) {
    this.codec = codec;
  }

  /**
   * Builds the cursor that follows a reading.
   *
   * @param observedAt moment of the reading
   * @param id id of the reading
   * @return the opaque text
   */
  public String encode(Instant observedAt, UUID id) {
    return codec.encode(LIST, observedAt.truncatedTo(ChronoUnit.MICROS) + "|" + id);
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
