package co.caudal.application.auth;

/**
 * What the login endpoint passes to the use case.
 *
 * @param username username as typed
 * @param password password as typed
 * @param ipAddress address of the caller, only hashed
 * @param userAgent user agent of the caller
 * @param mfaCode TOTP code of the second factor, or null if the person did not send one
 */
public record LoginInput(
    String username, String password, String ipAddress, String userAgent, String mfaCode) {

  /**
   * Input without a second factor code.
   *
   * @param username username as typed
   * @param password password as typed
   * @param ipAddress address of the caller
   * @param userAgent user agent of the caller
   */
  public LoginInput(String username, String password, String ipAddress, String userAgent) {
    this(username, password, ipAddress, userAgent, null);
  }

  @Override
  public String toString() {
    return "LoginInput[REDACTED]";
  }
}
