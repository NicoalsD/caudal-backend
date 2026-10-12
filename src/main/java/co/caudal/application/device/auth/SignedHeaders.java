package co.caudal.application.device.auth;

import java.util.List;

/**
 * The values of the four signature headers as they arrived, one list per header, so that a header
 * sent twice can be told apart from a header sent once.
 *
 * @param deviceIds the values of {@code X-Device-Id}
 * @param timestamps the values of {@code X-Timestamp}
 * @param nonces the values of {@code X-Nonce}
 * @param signatures the values of {@code X-Signature}
 */
public record SignedHeaders(
    List<String> deviceIds, List<String> timestamps, List<String> nonces, List<String> signatures) {

  /** Copies the lists so that the request keeps its own snapshot. */
  public SignedHeaders {
    deviceIds = List.copyOf(deviceIds);
    timestamps = List.copyOf(timestamps);
    nonces = List.copyOf(nonces);
    signatures = List.copyOf(signatures);
  }
}
