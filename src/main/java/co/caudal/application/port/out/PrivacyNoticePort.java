package co.caudal.application.port.out;

import co.caudal.application.privacy.PrivacyAcceptance;
import co.caudal.application.privacy.PrivacyNotice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads the privacy notice and records who accepted it ({@code iam.privacy_*}). */
public interface PrivacyNoticePort {

  /**
   * The version that applies now: the highest one already in effect.
   *
   * @param now the current moment
   * @return the version, or empty if there is none
   */
  Optional<PrivacyNotice> current(Instant now);

  /**
   * Records an acceptance, or returns the one already recorded.
   *
   * @param userId the account
   * @param version the version
   * @param ipHmac HMAC of the address of the person, or null
   * @param at moment of the acceptance
   * @return the acceptance as stored
   */
  PrivacyAcceptance accept(UUID userId, int version, String ipHmac, Instant at);
}
