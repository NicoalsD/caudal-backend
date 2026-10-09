package co.caudal.application.auth;

/**
 * What the refresh endpoint passes to the use case.
 *
 * @param refreshSecret raw value of the {@code caudal_rt} cookie, possibly null
 * @param ipAddress address of the caller, only hashed
 * @param userAgent user agent of the caller
 */
public record RefreshInput(String refreshSecret, String ipAddress, String userAgent) {

  @Override
  public String toString() {
    return "RefreshInput[REDACTED]";
  }
}
