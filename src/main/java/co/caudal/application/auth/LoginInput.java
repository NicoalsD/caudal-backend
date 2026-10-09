package co.caudal.application.auth;

/**
 * What the login endpoint passes to the use case.
 *
 * @param username username as typed
 * @param password password as typed
 * @param ipAddress address of the caller, only hashed
 * @param userAgent user agent of the caller
 */
public record LoginInput(String username, String password, String ipAddress, String userAgent) {

  @Override
  public String toString() {
    return "LoginInput[REDACTED]";
  }
}
