package co.caudal.infrastructure.config;

import co.caudal.application.catalog.GetCatalogUseCase;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.network.CreateTankCommandHandler;
import co.caudal.application.network.ListNetworkUseCase;
import co.caudal.application.network.SectorCommandHandlers;
import co.caudal.application.network.ValveCommandHandlers;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.SyncBatchPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.CorrectReadingCommandHandler;
import co.caudal.application.reading.CreateReadingBatchCommandHandler;
import co.caudal.application.reading.CreateReadingCommandHandler;
import co.caudal.application.reading.GetReadingUseCase;
import co.caudal.application.reading.ListReadingsUseCase;
import co.caudal.application.reading.ReadingCursor;
import co.caudal.application.reading.ReadingRecorder;
import co.caudal.application.rules.ActivateRuleSetCommandHandler;
import co.caudal.application.rules.CreateRuleSetDraftCommandHandler;
import co.caudal.application.rules.GetCurrentRuleSetUseCase;
import co.caudal.application.rules.GetRuleSetUseCase;
import co.caudal.application.rules.ListRuleSetsUseCase;
import co.caudal.application.rules.UpdateRuleSetDraftCommandHandler;
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
  ReadingRecorder readingRecorder(
      NetworkPort network, ReadingPort readings, CatalogPort catalogs, Clock clock) {
    return new ReadingRecorder(network, readings, catalogs, clock);
  }

  @Bean
  CreateReadingCommandHandler createReadingCommandHandler(
      RuleSetPort ruleSets, ReadingRecorder recorder, UnitOfWorkPort unitOfWork) {
    return new CreateReadingCommandHandler(ruleSets, recorder, unitOfWork);
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

  @Bean
  CreateRuleSetDraftCommandHandler createRuleSetDraftCommandHandler(
      RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    return new CreateRuleSetDraftCommandHandler(ruleSets, unitOfWork);
  }

  @Bean
  UpdateRuleSetDraftCommandHandler updateRuleSetDraftCommandHandler(
      RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    return new UpdateRuleSetDraftCommandHandler(ruleSets, unitOfWork);
  }

  @Bean
  ActivateRuleSetCommandHandler activateRuleSetCommandHandler(
      RuleSetPort ruleSets,
      NetworkPort network,
      DomainEventPublisher events,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ActivateRuleSetCommandHandler(ruleSets, network, events, unitOfWork, clock);
  }

  @Bean
  SectorCommandHandlers.Create createSectorCommandHandler(
      NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new SectorCommandHandlers.Create(network, unitOfWork);
  }

  @Bean
  SectorCommandHandlers.Update updateSectorCommandHandler(
      NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new SectorCommandHandlers.Update(network, unitOfWork);
  }

  @Bean
  ValveCommandHandlers.Create createValveCommandHandler(
      NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new ValveCommandHandlers.Create(network, unitOfWork);
  }

  @Bean
  ValveCommandHandlers.Update updateValveCommandHandler(
      NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new ValveCommandHandlers.Update(network, unitOfWork);
  }

  @Bean
  CreateReadingBatchCommandHandler createReadingBatchCommandHandler(
      RuleSetPort ruleSets,
      SyncBatchPort batches,
      ReadingRecorder recorder,
      UnitOfWorkPort unitOfWork) {
    return new CreateReadingBatchCommandHandler(ruleSets, batches, recorder, unitOfWork);
  }

  @Bean
  GetReadingUseCase getReadingUseCase(ReadingPort readings, UnitOfWorkPort unitOfWork) {
    return new GetReadingUseCase(readings, unitOfWork);
  }

  @Bean
  CorrectReadingCommandHandler correctReadingCommandHandler(
      ReadingPort readings, NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new CorrectReadingCommandHandler(readings, network, unitOfWork);
  }
}
