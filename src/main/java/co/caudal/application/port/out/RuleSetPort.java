package co.caudal.application.port.out;

import co.caudal.application.rules.RuleSetDetail;
import co.caudal.application.rules.RuleSetSummary;
import co.caudal.application.rules.RuleSetView;
import co.caudal.domain.rules.RuleSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the Board's rule sets. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the rule tables have row level security.
 */
public interface RuleSetPort {

  /**
   * Finds the ACTIVE version of the aqueduct of the session.
   *
   * @return the version with its children, or empty
   */
  Optional<RuleSetView> findActive();

  /**
   * Finds a version of the aqueduct of the session.
   *
   * @param id the version
   * @return the version with its children, or empty
   */
  Optional<RuleSetView> findById(UUID id);

  /**
   * Lists versions of the aqueduct of the session, newest first.
   *
   * @param status only this status, or null for all
   * @param beforeVersion only versions with a lower number, or null from the newest
   * @param limit maximum number of versions
   * @return the versions
   */
  List<RuleSetSummary> list(String status, Integer beforeVersion, int limit);

  /**
   * Finds a version of the aqueduct of the session with its history data.
   *
   * @param id the version
   * @return the version, or empty
   */
  Optional<RuleSetDetail> findDetail(UUID id);

  /**
   * Tells whether the aqueduct of the session already has a draft.
   *
   * @return true if a version is in {@code DRAFT}
   */
  boolean draftExists();

  /**
   * The highest version number of the aqueduct of the session.
   *
   * @return the number, or 0 if there is none
   */
  int maxVersion();

  /**
   * Stores a draft with its children.
   *
   * @param aqueductId the aqueduct of the session
   * @param draft the draft
   * @param createdBy who creates it
   * @param changeReason why, or null
   * @return the id of the new version
   */
  UUID insertDraft(UUID aqueductId, RuleSet draft, UUID createdBy, String changeReason);

  /**
   * Saves the scalar parameters and the reason of a draft.
   *
   * @param ruleSetId the draft
   * @param parameters the parameters
   * @param changeReason the reason, or null
   * @throws co.caudal.shared.error.DomainException with {@code RULE_SET_INVALID} if the database
   *     refuses a value
   */
  void updateDraft(UUID ruleSetId, RuleSet.Parameters parameters, String changeReason);

  /**
   * Replaces the bands, sector settings, valve order and windows of a draft.
   *
   * @param ruleSetId the draft
   * @param rules the version with the new children
   * @throws co.caudal.shared.error.DomainException with {@code RULE_SET_INVALID} if the database
   *     refuses them (overlapping bands, a sector or valve of another aqueduct, a repeated order)
   */
  void replaceChildren(UUID ruleSetId, RuleSet rules);

  /**
   * Marks the active version as replaced.
   *
   * @param ruleSetId the active version
   * @param validTo end of its validity, the start of the new one
   */
  void supersede(UUID ruleSetId, Instant validTo);

  /**
   * Puts a draft in force.
   *
   * @param ruleSetId the draft
   * @param validFrom start of its validity
   * @param activatedBy who activates it
   * @param changeReason why
   */
  void activate(UUID ruleSetId, Instant validFrom, UUID activatedBy, String changeReason);
}
