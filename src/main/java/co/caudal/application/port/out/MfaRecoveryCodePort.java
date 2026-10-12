package co.caudal.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stores the one-time recovery codes of the second factor ({@code iam.mfa_recovery_codes}). */
public interface MfaRecoveryCodePort {

  /**
   * Stores new recovery codes.
   *
   * @param userId the account
   * @param codeHashes SHA-256 of each code; the codes themselves are never stored
   */
  void insertAll(UUID userId, List<String> codeHashes);

  /**
   * Uses a recovery code: marks it as used if it belongs to the account and was not used yet.
   *
   * @param userId the account
   * @param codeHash SHA-256 of the typed code
   * @param at moment of use
   * @return true if the code was valid and is now used
   */
  boolean consume(UUID userId, String codeHash, Instant at);
}
