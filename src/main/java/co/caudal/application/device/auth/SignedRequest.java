package co.caudal.application.device.auth;

import co.caudal.domain.device.CanonicalRequest;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * A request of a device with its signature headers parsed (docs/Protocolo-de-dispositivos.md,
 * section 2.1). A header that is missing or sent twice is {@code 401 INVALID_SIGNATURE}; a header
 * whose format is wrong is {@code 400 VALIDATION_ERROR}.
 *
 * @param deviceId the {@code X-Device-Id} header
 * @param method the HTTP method as sent
 * @param path the path with the {@code /api/v1} prefix and the query string, if any
 * @param timestampText the {@code X-Timestamp} header exactly as sent
 * @param timestamp the same value as seconds since the epoch
 * @param nonce the {@code X-Nonce} header
 * @param signature the 64 bytes of the {@code X-Signature} header
 * @param body the raw body
 * @param ipAddress the address of the connection, or null if unknown
 */
public record SignedRequest(
    UUID deviceId,
    String method,
    String path,
    String timestampText,
    long timestamp,
    String nonce,
    byte[] signature,
    byte[] body,
    String ipAddress) {

  /**
   * Parses the signature headers of a request.
   *
   * @param method the HTTP method as sent
   * @param path the path with the query string, if any
   * @param headers the values of the four headers
   * @param body the raw body
   * @param ipAddress the address of the connection, or null
   * @return the parsed request
   * @throws DeviceAuthFailure with {@code INVALID_SIGNATURE} if a header is missing or repeated
   * @throws FieldValidationException if a header has the wrong format
   */
  public static SignedRequest of(
      String method, String path, SignedHeaders headers, byte[] body, String ipAddress) {
    String deviceText = single(headers.deviceIds());
    String timestampText = single(headers.timestamps());
    String nonce = single(headers.nonces());
    String signatureText = single(headers.signatures());
    UUID deviceId =
        UUID.fromString(format(deviceText, FieldLimits.DEVICE_ID_PATTERN, "X-Device-Id"));
    format(timestampText, FieldLimits.DEVICE_TIMESTAMP_PATTERN, "X-Timestamp");
    format(nonce, FieldLimits.DEVICE_NONCE_PATTERN, "X-Nonce");
    byte[] signature = decodeSignature(signatureText);
    return new SignedRequest(
        deviceId,
        method,
        path,
        timestampText,
        Long.parseLong(timestampText),
        nonce,
        signature,
        body.clone(),
        ipAddress);
  }

  /**
   * The canonical string of this request (docs/Protocolo-de-dispositivos.md, section 2.2).
   *
   * @return the bytes that the device signed
   */
  public byte[] canonical() {
    return CanonicalRequest.of(method, path, timestampText, nonce, body);
  }

  private static String single(List<String> values) {
    if (values.size() != 1) {
      throw new DeviceAuthFailure(ErrorCode.INVALID_SIGNATURE);
    }
    return values.get(0);
  }

  private static String format(String value, String pattern, String field) {
    if (!value.matches(pattern)) {
      throw new FieldValidationException(field, "FIELD_FORMAT_INVALID");
    }
    return value;
  }

  /** Decodes the signature, which must be 64 bytes with no unused bits set. */
  private static byte[] decodeSignature(String text) {
    format(text, FieldLimits.DEVICE_SIGNATURE_PATTERN, "X-Signature");
    byte[] bytes = Base64.getUrlDecoder().decode(text);
    if (bytes.length != FieldLimits.DEVICE_SIGNATURE_BYTES
        || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(text)) {
      throw new FieldValidationException("X-Signature", "FIELD_FORMAT_INVALID");
    }
    return bytes;
  }
}
