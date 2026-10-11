package co.caudal.domain.mfa;

import java.io.ByteArrayOutputStream;

/** Base32 without padding (RFC 4648, section 6), the alphabet authenticator apps expect. */
final class Base32 {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final int BITS_PER_CHAR = 5;
  private static final int BITS_PER_BYTE = 8;
  private static final int CHAR_MASK = 0x1F;
  private static final int BYTE_MASK = 0xFF;

  private Base32() {}

  static String encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte value : data) {
      buffer = (buffer << BITS_PER_BYTE) | (value & BYTE_MASK);
      bits += BITS_PER_BYTE;
      while (bits >= BITS_PER_CHAR) {
        out.append(ALPHABET.charAt((buffer >> (bits - BITS_PER_CHAR)) & CHAR_MASK));
        bits -= BITS_PER_CHAR;
      }
    }
    if (bits > 0) {
      out.append(ALPHABET.charAt((buffer << (BITS_PER_CHAR - bits)) & CHAR_MASK));
    }
    return out.toString();
  }

  /** Decodes upper case Base32 without padding, as {@link #encode} writes it. */
  static byte[] decode(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bits = 0;
    for (char c : text.toCharArray()) {
      int value = ALPHABET.indexOf(c);
      if (value < 0) {
        throw new IllegalArgumentException("Not Base32");
      }
      buffer = (buffer << BITS_PER_CHAR) | value;
      bits += BITS_PER_CHAR;
      if (bits >= BITS_PER_BYTE) {
        out.write((buffer >> (bits - BITS_PER_BYTE)) & BYTE_MASK);
        bits -= BITS_PER_BYTE;
      }
    }
    return out.toByteArray();
  }
}
