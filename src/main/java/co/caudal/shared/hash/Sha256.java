package co.caudal.shared.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 in lower case hexadecimal, for the hashes of the documents the system publishes. */
public final class Sha256 {

  private static final String ALGORITHM = "SHA-256";

  private Sha256() {}

  /**
   * The hash of some bytes.
   *
   * @param bytes the content
   * @return 64 hexadecimal characters
   */
  public static String hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITHM).digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
