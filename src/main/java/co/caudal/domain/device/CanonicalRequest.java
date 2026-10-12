package co.caudal.domain.device;

import co.caudal.shared.hash.Sha256;
import java.nio.charset.StandardCharsets;

/**
 * The canonical string that a device signs (docs/Protocolo-de-dispositivos.md, section 2.2): five
 * lines separated by LF, without a final LF, encoded in UTF-8. The body enters as the SHA-256 of
 * its raw bytes, so the signature covers exactly what travels on the wire.
 */
public final class CanonicalRequest {

  /** Separator between the lines of the canonical string. */
  private static final String LINE_SEPARATOR = "\n";

  private CanonicalRequest() {}

  /**
   * Builds the bytes that the device signed.
   *
   * @param method the HTTP method as sent, for example {@code POST}
   * @param path the path with the {@code /api/v1} prefix and the query string, if any
   * @param timestamp the {@code X-Timestamp} header exactly as sent
   * @param nonce the {@code X-Nonce} header exactly as sent
   * @param body the raw bytes of the body, empty for a request without body
   * @return the UTF-8 bytes of the five lines
   */
  public static byte[] of(String method, String path, String timestamp, String nonce, byte[] body) {
    return String.join(LINE_SEPARATOR, method, path, timestamp, nonce, Sha256.hex(body))
        .getBytes(StandardCharsets.UTF_8);
  }
}
