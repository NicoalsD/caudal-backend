package co.caudal.infrastructure.config;

import co.caudal.application.command.CommandBus;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.SimpleCommandBus;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers every {@link CommandHandler} in the command bus (P13). */
@Configuration(proxyBeanMethods = false)
class CommandBusConfig {

  @Bean
  CommandBus commandBus(List<CommandHandler<?, ?>> handlers) {
    return new SimpleCommandBus(handlers);
  }
}
