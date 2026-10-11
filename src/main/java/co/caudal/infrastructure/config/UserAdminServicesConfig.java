package co.caudal.infrastructure.config;

import co.caudal.application.auth.SessionInvalidator;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.application.user.UpdateUserCommandHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use cases of the administration of accounts and memberships by the Board. */
@Configuration(proxyBeanMethods = false)
class UserAdminServicesConfig {

  @Bean
  UpdateUserCommandHandler updateUserCommandHandler(
      UserAdminPort users, SessionInvalidator sessions, UnitOfWorkPort unitOfWork, Clock clock) {
    return new UpdateUserCommandHandler(users, sessions, unitOfWork, clock);
  }
}
