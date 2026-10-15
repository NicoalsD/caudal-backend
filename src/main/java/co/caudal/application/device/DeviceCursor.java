package co.caudal.application.device;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cursor of the list of devices (docs/API.md, section 1.5): the moment the device was registered
 * and the id of the last device of a page, signed by {@link SignedCursorCodec}.
 */
public class DeviceCursor {

  /**
   * A position in the list.
   *
   * @param createdAt the registration moment of the last device of the page
   * @param id the id of the last device of the page
   */
  public record Position(Instant createdAt, UUID id) {}

  private static final String LIST = "device";
  private static final char BAR = '|';

  private final SignedCursorCodec codec;

  /**
   * Creates the cursor codec of devices.
   *
   * @param codec signs and verifies the position
   */
  public DeviceCursor(SignedCursorCodec codec) {
    this.codec = codec;
  }

  /**
   * Builds the cursor that follows a device. The moment is cut to microseconds, the precision of
   * the database, so that the next page neither skips nor repeats rows.
   *
   * @param createdAt when the device was registered
   * @param id id of the device
   * @return the opaque text
   */
  public String encode(Instant createdAt, UUID id) {
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
          Instant.parse(position.substring(0, bar)), UUID.fromString(position.substring(bar + 1)));
    } catch (DateTimeParseException | IllegalArgumentException | IndexOutOfBoundsException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
