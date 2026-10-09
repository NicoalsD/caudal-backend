package co.caudal.application.auth;

import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.function.Supplier;

/** Unit of work without a transaction: runs the work directly (use case tests). */
final class DirectUnitOfWork implements UnitOfWorkPort {

  @Override
  public <T> T execute(Supplier<T> work) {
    return work.get();
  }

  @Override
  public <T> T executeAs(SessionScope scope, Supplier<T> work) {
    return work.get();
  }
}
