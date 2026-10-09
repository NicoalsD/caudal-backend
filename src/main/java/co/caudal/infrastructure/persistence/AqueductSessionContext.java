package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.SessionScope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fixes {@code app.aqueduct_id} and {@code app.user_id} for the current transaction with {@code
 * set_config(..., true)} (equivalent to {@code SET LOCAL}). Must be called inside a transaction.
 * Without a value the policies see an empty setting and return no rows (fail closed).
 */
@Component
public class AqueductSessionContext {

  private static final String SQL =
      "SELECT set_config('app.aqueduct_id', :aqueductId, true),"
          + " set_config('app.user_id', :userId, true)";

  private final JdbcClient jdbc;

  AqueductSessionContext(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Applies the scope to the current transaction.
   *
   * @param scope who the transaction acts for
   */
  public void apply(SessionScope scope) {
    jdbc.sql(SQL)
        .param("aqueductId", scope.aqueductId() == null ? "" : scope.aqueductId().toString())
        .param("userId", scope.userId() == null ? "" : scope.userId().toString())
        .query()
        .singleRow();
  }
}
