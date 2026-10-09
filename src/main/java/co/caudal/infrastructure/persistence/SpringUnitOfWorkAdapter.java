package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the work of a use case in one Spring transaction. */
@Component
public class SpringUnitOfWorkAdapter implements UnitOfWorkPort {

  private final TransactionTemplate transaction;

  SpringUnitOfWorkAdapter(PlatformTransactionManager transactionManager) {
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T execute(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }
}
