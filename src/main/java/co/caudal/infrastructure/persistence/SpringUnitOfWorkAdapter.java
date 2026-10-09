package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the work of a use case in one Spring transaction. */
@Component
public class SpringUnitOfWorkAdapter implements UnitOfWorkPort {

  private final TransactionTemplate transaction;
  private final AqueductSessionContext sessionContext;

  SpringUnitOfWorkAdapter(
      PlatformTransactionManager transactionManager, AqueductSessionContext sessionContext) {
    this.transaction = new TransactionTemplate(transactionManager);
    this.sessionContext = sessionContext;
  }

  @Override
  public <T> T execute(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  @Override
  public <T> T executeAs(SessionScope scope, Supplier<T> work) {
    return transaction.execute(
        status -> {
          sessionContext.apply(scope);
          return work.get();
        });
  }
}
