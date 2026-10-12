package co.caudal.application.mfa;

/**
 * What the person needs to add CAUDAL to an authenticator app. It is shown once.
 *
 * @param secret the secret in Base32, to type it
 * @param otpauthUri the URI for the QR code
 */
public record MfaEnrollment(String secret, String otpauthUri) {

  @Override
  public String toString() {
    return "MfaEnrollment[REDACTED]";
  }
}
