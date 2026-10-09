package co.caudal.application.port.out;

import co.caudal.application.auth.SessionScope;
import java.util.function.Supplier;

/**
 * Runs a piece of work in one database transaction. Use cases never open transactions themselves.
 *
 * <p>If the work throws, the transaction is rolled back. To keep a change and still report a
 * failure, the work returns a result and the caller throws after the commit.
 */
public interface UnitOfWorkPort {

  /**
   * Runs the work in a transaction.
   *
   * @param work the work to run
   * @param <T> the type of the result
   * @return what the work returned, after the commit
   */
  <T> T execute(Supplier<T> work);

  /**
   * Runs the work in a transaction that carries the session scope for row level security: the scope
   * is set with {@code set_config(..., true)}, so it lasts only until commit or rollback and never
   * leaks to another request that shares the connection.
   *
   * @param scope who the work acts for
   * @param work the work to run
   * @param <T> the type of the result
   * @return what the work returned, after the commit
   */
  <T> T executeAs(SessionScope scope, Supplier<T> work);
}
