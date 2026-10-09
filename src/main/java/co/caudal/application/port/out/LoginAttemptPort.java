package co.caudal.application.port.out;

import co.caudal.application.auth.LoginAttempt;
import java.time.Instant;
import java.util.UUID;

/** Records and counts login attempts ({@code iam.login_attempts}). */
public interface LoginAttemptPort {

  /**
   * Records an attempt.
   *
   * @param attempt the attempt
   */
  void record(LoginAttempt attempt);

  /**
   * Counts the consecutive failed attempts of an account since a moment: failures after its last
   * success.
   *
   * @param userId the account
   * @param since start of the window
   * @return the number of failures
   */
  int countConsecutiveFailures(UUID userId, Instant since);

  /**
   * Counts the failed attempts from an IP since a moment, whatever the account. Attempts refused by
   * the limit itself are not counted, so the window slides.
   *
   * @param ipHmac keyed hash of the IP
   * @param since start of the window
   * @return the number of failures
   */
  int countFailuresByIp(String ipHmac, Instant since);
}
