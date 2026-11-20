package co.caudal.application.auth;

/**
 * What the login endpoint passes to the use case.
 *
 * @param username username as typed
 * @param password password as typed
 * @param ipAddress address of the caller, only hashed
 * @param userAgent user agent of the caller
 * @param mfaCode TOTP code of the second factor, or null if the person did not send one
 * @param institutionSlug slug of the institution, or null for the single-step login (the username
 *     must then be unique)
 */
public record LoginInput(
    String username,
    String password,
    String ipAddress,
    String userAgent,
    String mfaCode,
    String institutionSlug) {

  /**
   * Input without an institution (single-step login).
   *
   * @param username username as typed
   * @param password password as typed
   * @param ipAddress address of the caller
   * @param userAgent user agent of the caller
   * @param mfaCode TOTP code of the second factor, or null
   */
  public LoginInput(
      String username, String password, String ipAddress, String userAgent, String mfaCode) {
    this(username, password, ipAddress, userAgent, mfaCode, null);
  }

  /**
   * Input without a second factor code or institution.
   *
   * @param username username as typed
   * @param password password as typed
   * @param ipAddress address of the caller
   * @param userAgent user agent of the caller
   */
  public LoginInput(String username, String password, String ipAddress, String userAgent) {
    this(username, password, ipAddress, userAgent, null, null);
  }

  @Override
  public String toString() {
    return "LoginInput[REDACTED]";
  }
}
