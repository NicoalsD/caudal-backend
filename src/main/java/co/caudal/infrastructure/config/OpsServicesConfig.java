package co.caudal.infrastructure.config;

import co.caudal.application.network.CreateTankCommandHandler;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.GetCurrentRuleSetUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the operation of the aqueduct (rules, network, readings, schedule). */
@Configuration(proxyBeanMethods = false)
class OpsServicesConfig {

  @Bean
  GetCurrentRuleSetUseCase getCurrentRuleSetUseCase(
      RuleSetPort ruleSets, NetworkPort network, UnitOfWorkPort unitOfWork, Clock clock) {
    return new GetCurrentRuleSetUseCase(ruleSets, network, unitOfWork, clock);
  }

  @Bean
  ListNetworkUseCase listNetworkUseCase(NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new ListNetworkUseCase(network, unitOfWork);
  }

  @Bean
  CreateTankCommandHandler createTankCommandHandler(
      NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new CreateTankCommandHandler(network, unitOfWork);
  }
}
