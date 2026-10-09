package co.caudal.application.port.out;

/** Keyed hash that lets the system compare personal data without storing it. */
public interface PrivacyHasherPort {

  /**
   * Hashes a value (an IP address or a username) with the secret key.
   *
   * @param value the value
   * @return 64 lower case hexadecimal characters
   */
  String hmac(String value);
}
