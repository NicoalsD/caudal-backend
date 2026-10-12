package co.caudal.application.pagination;

import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Opaque, signed pagination cursors (docs/API.md, section 1.5). A cursor is the position of the
 * last item of a page in Base64URL plus an HMAC of that position and of the list it belongs to, so
 * a cursor that was altered, or that comes from another list, answers {@code CURSOR_INVALID}.
 */
public class SignedCursorCodec {

  private static final String SEPARATOR = ".";
  private static final String PURPOSE_SUFFIX = "-cursor|";

  private final PrivacyHasherPort signer;

  /**
   * Creates the codec.
   *
   * @param signer produces the HMAC of a text
   */
  public SignedCursorCodec(PrivacyHasherPort signer) {
    this.signer = signer;
  }

  /**
   * Builds a cursor.
   *
   * @param list name of the list (for example {@code users})
   * @param position the position of the last item of the page
   * @return the opaque text
   */
  public String encode(String list, String position) {
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(position.getBytes(StandardCharsets.UTF_8))
        + SEPARATOR
        + signer.hmac(list + PURPOSE_SUFFIX + position);
  }

  /**
   * Reads and verifies a cursor.
   *
   * @param list name of the list the cursor must belong to
   * @param cursor the text from the client
   * @return the position
   * @throws DomainException with {@code CURSOR_INVALID} if it is malformed, altered or foreign
   */
  public String decode(String list, String cursor) {
    int dot = cursor.indexOf(SEPARATOR);
    if (dot <= 0) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
    String position;
    try {
      position =
          new String(
              Base64.getUrlDecoder().decode(cursor.substring(0, dot)), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
    byte[] expected =
        signer.hmac(list + PURPOSE_SUFFIX + position).getBytes(StandardCharsets.UTF_8);
    byte[] received = cursor.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, received)) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
    return position;
  }
}
