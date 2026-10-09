package co.caudal.domain.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Opaque secret of 256 random bits that proves a session (the refresh token).
 *
 * <p>The raw value is shown to the client once and never stored. The database keeps only {@link
 * #hash()}, the lower case hexadecimal SHA-256 of the raw value, so a leaked table cannot be used
 * to open sessions. A SHA-256 is enough here (and Argon2 would be wasteful) because the secret is
 * long and random, not a password a person chose.
 *
 * @param raw the secret as sent to the client (base64url without padding)
 */
public record OpaqueToken(String raw) {

  /** Size of the secret, in bytes (256 bits). */
  public static final int SIZE_BYTES = 32;

  /** Longest presented value that is worth hashing; anything longer cannot be one of ours. */
  public static final int MAX_PRESENTED_LENGTH = 128;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String HASH_ALGORITHM = "SHA-256";

  /**
   * Keeps the raw value.
   *
   * @throws NullPointerException if the value is null
   */
  public OpaqueToken {
    Objects.requireNonNull(raw, "raw");
  }

  /**
   * Generates a new random secret.
   *
   * @return a token with 256 bits of entropy
   */
  public static OpaqueToken generate() {
    byte[] bytes = new byte[SIZE_BYTES];
    RANDOM.nextBytes(bytes);
    return new OpaqueToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }

  /**
   * Hashes a secret presented by a client.
   *
   * @param presented the raw value that arrived in the cookie
   * @return the lower case hexadecimal SHA-256, 64 characters
   */
  public static String hashOf(String presented) {
    try {
      byte[] digest =
          MessageDigest.getInstance(HASH_ALGORITHM)
              .digest(presented.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(HASH_ALGORITHM + " is required by the Java platform", ex);
    }
  }

  /**
   * Tells whether a presented value could be one of our tokens, before spending a hash on it.
   *
   * @param presented the raw value that arrived in the cookie, possibly null
   * @return true if it is present and not absurdly long
   */
  public static boolean isPlausible(String presented) {
    return presented != null && !presented.isEmpty() && presented.length() <= MAX_PRESENTED_LENGTH;
  }

  /**
   * Hash to store.
   *
   * @return the lower case hexadecimal SHA-256 of the raw value
   */
  public String hash() {
    return hashOf(raw);
  }

  @Override
  public String toString() {
    return "OpaqueToken[REDACTED]";
  }
}
