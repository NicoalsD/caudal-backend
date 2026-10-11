package co.caudal.application.port.out;

import co.caudal.application.rules.RuleSetSummary;
import co.caudal.application.rules.RuleSetView;
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
}
