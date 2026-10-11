package co.caudal.application.reading;

import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque, signed cursor of the list of readings (docs/API.md, section 1.5). It carries the position
 * (observation moment and id) and an HMAC, so a cursor that was altered answers {@code
 * CURSOR_INVALID}.
 */
public class ReadingCursor {

  /**
   * A position in the list.
   *
   * @param observedAt the moment of the last reading of the page
   * @param id the id of the last reading of the page
   */
  public record Position(Instant observedAt, UUID id) {}

  private static final String SEPARATOR = ".";

  /** Purpose tag signed with the payload so a cursor signature never equals any other HMAC. */
  private static final String PURPOSE = "reading-cursor|";

  private final PrivacyHasherPort signer;

  /**
   * Creates the codec.
   *
   * @param signer produces the HMAC of a text
   */
  public ReadingCursor(PrivacyHasherPort signer) {
    this.signer = signer;
  }

  /**
   * Builds the cursor that follows a reading.
   *
   * @param observedAt moment of the reading
   * @param id id of the reading
   * @return the opaque text
   */
  public String encode(Instant observedAt, UUID id) {
    String payload = observedAt.truncatedTo(ChronoUnit.MICROS) + "|" + id;
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        + SEPARATOR
        + signer.hmac(PURPOSE + payload);
  }

  /**
   * Reads and verifies a cursor.
   *
   * @param cursor the text from the client
   * @return the position
   * @throws DomainException with {@code CURSOR_INVALID} if it is malformed or altered
   */
  public Position decode(String cursor) {
    try {
      int dot = cursor.indexOf(SEPARATOR);
      String payload =
          new String(
              Base64.getUrlDecoder().decode(cursor.substring(0, dot)), StandardCharsets.UTF_8);
      String signature = cursor.substring(dot + 1);
      if (!MessageDigest.isEqual(
          signer.hmac(PURPOSE + payload).getBytes(StandardCharsets.UTF_8),
          signature.getBytes(StandardCharsets.UTF_8))) {
        throw new DomainException(ErrorCode.CURSOR_INVALID);
      }
      int bar = payload.indexOf('|');
      return new Position(
          Instant.parse(payload.substring(0, bar)), UUID.fromString(payload.substring(bar + 1)));
    } catch (RuntimeException malformed) {
      if (malformed instanceof DomainException domain) {
        throw domain;
      }
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
