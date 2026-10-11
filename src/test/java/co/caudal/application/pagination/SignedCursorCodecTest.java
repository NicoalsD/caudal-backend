package co.caudal.application.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SignedCursorCodecTest {

  /** A keyed stand-in for the HMAC: SHA-256 of a secret plus the text. */
  private static final PrivacyHasherPort SIGNER =
      value -> {
        try {
          return HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(("secret:" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
          throw new IllegalStateException(impossible);
        }
      };

  private final SignedCursorCodec codec = new SignedCursorCodec(SIGNER);

  @Test
  void aCursorReturnsItsPosition() {
    String cursor = codec.encode("users", "operador.uno");

    assertThat(codec.decode("users", cursor)).isEqualTo("operador.uno");
    assertThat(cursor).doesNotContain("operador");
  }

  @Test
  void aCursorOfAnotherListIsInvalid() {
    String cursor = codec.encode("users", "operador.uno");

    assertInvalid("memberships", cursor);
  }

  @Test
  void anAlteredOrMalformedCursorIsInvalid() {
    String cursor = codec.encode("users", "operador.uno");
    String forged =
        codec.encode("users", "zzz").split("\\.")[0] + cursor.substring(cursor.indexOf('.'));

    assertInvalid("users", cursor + "x");
    assertInvalid("users", forged);
    assertInvalid("users", "no-dot");
    assertInvalid("users", ".only-signature");
    assertInvalid("users", "%%%." + cursor.substring(cursor.indexOf('.') + 1));
  }

  private void assertInvalid(String list, String cursor) {
    assertThatThrownBy(() -> codec.decode(list, cursor))
        .isInstanceOf(DomainException.class)
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.CURSOR_INVALID);
  }
}
