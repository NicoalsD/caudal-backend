package co.caudal.application.port.out;

import java.util.OptionalInt;
import java.util.UUID;

/** Reads the current token version of an account, to reject access tokens that are out of date. */
public interface TokenVersionPort {

  /**
   * Finds the current {@code token_version} of an account that can still hold a session.
   *
   * @param userId the account
   * @return the version, or empty if the account does not exist or is disabled
   */
  OptionalInt findCurrentVersion(UUID userId);
}
