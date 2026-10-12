package co.caudal.application.port.out;

import co.caudal.application.rules.RuleSetView;
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
}
