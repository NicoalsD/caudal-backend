package co.caudal.infrastructure.config;

import co.caudal.application.catalog.GetCatalogUseCase;
import co.caudal.application.network.CreateTankCommandHandler;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.CreateReadingCommandHandler;
import co.caudal.application.reading.ListReadingsUseCase;
import co.caudal.application.reading.ReadingCursor;
import co.caudal.application.rules.GetCurrentRuleSetUseCase;
import co.caudal.application.rules.GetRuleSetUseCase;
import co.caudal.application.rules.ListRuleSetsUseCase;
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
  ReadingCursor readingCursor(SignedCursorCodec codec) {
    return new ReadingCursor(codec);
  }

  @Bean
  ListReadingsUseCase listReadingsUseCase(
      NetworkPort network, ReadingPort readings, ReadingCursor cursors, UnitOfWorkPort unitOfWork) {
    return new ListReadingsUseCase(network, readings, cursors, unitOfWork);
  }

  @Bean
  ListRuleSetsUseCase listRuleSetsUseCase(
      RuleSetPort ruleSets, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    return new ListRuleSetsUseCase(ruleSets, cursors, unitOfWork);
  }

  @Bean
  GetRuleSetUseCase getRuleSetUseCase(RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    return new GetRuleSetUseCase(ruleSets, unitOfWork);
  }
}
