package co.caudal.application.port.out;

import co.caudal.application.auth.UserProfile;
import java.util.Optional;
import java.util.UUID;

/** Reads the profile of an account. */
public interface UserProfilePort {

  /**
   * Finds the profile of an account.
   *
   * @param userId the account
   * @return the profile, or empty if the account does not exist
   */
  Optional<UserProfile> findProfile(UUID userId);
}
