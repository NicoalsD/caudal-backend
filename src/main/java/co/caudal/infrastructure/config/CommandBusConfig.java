package co.caudal.infrastructure.config;

import co.caudal.application.command.AuditingCommandHandler;
import co.caudal.application.command.Command;
import co.caudal.application.command.CommandBus;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.SimpleCommandBus;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers every {@link CommandHandler} in the command bus (P13), each one wrapped in an {@link
 * AuditingCommandHandler} (P09) that writes the audit log and the denied permissions.
 */
@Configuration(proxyBeanMethods = false)
class CommandBusConfig {

  @Bean
  CommandBus commandBus(
      List<CommandHandler<?, ?>> handlers,
      AuditLogPort auditLog,
      SecurityEventPort events,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork) {
    return new SimpleCommandBus(
        handlers.stream()
            .map(handler -> audited(handler, auditLog, events, hasher, unitOfWork))
            .toList());
  }

  private static <C extends Command<R>, R> CommandHandler<C, R> audited(
      CommandHandler<C, R> handler,
      AuditLogPort auditLog,
      SecurityEventPort events,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork) {
    return new AuditingCommandHandler<>(handler, auditLog, events, hasher, unitOfWork);
  }
}
