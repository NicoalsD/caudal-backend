package co.caudal.infrastructure.config;

import co.caudal.application.catalog.GetCatalogUseCase;
import co.caudal.application.network.CreateTankCommandHandler;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.CreateReadingCommandHandler;
import co.caudal.application.reading.ListReadingsUseCase;
import co.caudal.application.reading.ReadingCursor;
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

  @Bean
  GetCatalogUseCase getCatalogUseCase(CatalogPort catalogs) {
    return new GetCatalogUseCase(catalogs);
  }

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  CreateReadingCommandHandler createReadingCommandHandler(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      CatalogPort catalogs,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new CreateReadingCommandHandler(
        network, ruleSets, readings, catalogs, unitOfWork, clock);
  }

  @Bean
  ReadingCursor readingCursor(PrivacyHasherPort hasher) {
    return new ReadingCursor(hasher);
  }

  @Bean
  ListReadingsUseCase listReadingsUseCase(
      NetworkPort network, ReadingPort readings, ReadingCursor cursors, UnitOfWorkPort unitOfWork) {
    return new ListReadingsUseCase(network, readings, cursors, unitOfWork);
  }
}
