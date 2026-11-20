package co.caudal.infrastructure.config;

import co.caudal.application.autopilot.AcceptDecisionHandler;
import co.caudal.application.autopilot.AssistantFacade;
import co.caudal.application.autopilot.AutopilotCommandFactory;
import co.caudal.application.autopilot.AutopilotCycle;
import co.caudal.application.autopilot.AutopilotRunner;
import co.caudal.application.autopilot.DecisionExecutionObserver;
import co.caudal.application.autopilot.ExecuteDecisionHandler;
import co.caudal.application.autopilot.GetSettingsUseCase;
import co.caudal.application.autopilot.ListDecisionsUseCase;
import co.caudal.application.autopilot.ListLiveValvesUseCase;
import co.caudal.application.autopilot.OperatorPresenceTracker;
import co.caudal.application.autopilot.RejectDecisionHandler;
import co.caudal.application.autopilot.ReportUseCases;
import co.caudal.application.autopilot.RunAutopilotUseCase;
import co.caudal.application.autopilot.SnapshotBuilder;
import co.caudal.application.autopilot.UpdateSettingsHandler;
import co.caudal.application.command.CommandBus;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.AutopilotContextPort;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.ReportPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.ValveCommandPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Creates the use cases of the autopilot, the assistant and the reports (HU-40 to HU-44). */
@Configuration(proxyBeanMethods = false)
class AutopilotServicesConfig {

  @Bean
  OperatorPresenceTracker operatorPresenceTracker() {
    return new OperatorPresenceTracker();
  }

  @Bean
  DecisionExecutionObserver decisionExecutionObserver(AutopilotDecisionPort decisions) {
    return new DecisionExecutionObserver(decisions);
  }

  @Bean
  SnapshotBuilder snapshotBuilder(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      ForecastRunPort forecasts,
      AutopilotContextPort context,
      AqueductPort aqueducts,
      Clock clock) {
    return new SnapshotBuilder(network, ruleSets, readings, forecasts, context, aqueducts, clock);
  }

  @Bean
  AutopilotCommandFactory autopilotCommandFactory(
      DevicePort devices,
      NetworkPort network,
      ValveCommandPort commands,
      DeviceProperties properties,
      Clock clock) {
    return new AutopilotCommandFactory(
        devices,
        network,
        commands,
        Duration.ofMinutes(properties.valveCommandWindowMinutes()),
        clock);
  }

  @Bean
  UpdateSettingsHandler updateAutopilotSettingsHandler(
      AutopilotSettingsPort settings, UnitOfWorkPort unitOfWork) {
    return new UpdateSettingsHandler(settings, unitOfWork);
  }

  @Bean
  AcceptDecisionHandler acceptDecisionHandler(
      AutopilotDecisionPort decisions,
      AutopilotSettingsPort settings,
      AutopilotCommandFactory factory,
      SnapshotBuilder snapshots,
      DomainEventPublisher events,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new AcceptDecisionHandler(
        decisions, settings, factory, snapshots, events, unitOfWork, clock);
  }

  @Bean
  RejectDecisionHandler rejectDecisionHandler(
      AutopilotDecisionPort decisions,
      DomainEventPublisher events,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new RejectDecisionHandler(decisions, events, unitOfWork, clock);
  }

  @Bean
  ExecuteDecisionHandler executeDecisionHandler(
      AutopilotDecisionPort decisions,
      AutopilotCommandFactory factory,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ExecuteDecisionHandler(decisions, factory, unitOfWork, clock);
  }

  // The command bus collects every handler, and the cycle dispatches through it: @Lazy breaks the
  // loop between the bus and the cycle.
  @Bean
  AutopilotCycle autopilotCycle(
      SnapshotBuilder snapshots,
      AutopilotSettingsPort settings,
      AutopilotDecisionPort decisions,
      InsightsPort insights,
      OperatorPresenceTracker presence,
      @Lazy CommandBus commandBus,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new AutopilotCycle(
        snapshots, settings, decisions, insights, presence, commandBus, unitOfWork, clock);
  }

  @Bean
  AutopilotRunner autopilotRunner(AqueductPort aqueducts, AutopilotCycle cycle) {
    return new AutopilotRunner(aqueducts, cycle);
  }

  @Bean
  GetSettingsUseCase getAutopilotSettingsUseCase(
      AutopilotSettingsPort settings, UnitOfWorkPort unitOfWork) {
    return new GetSettingsUseCase(settings, unitOfWork);
  }

  @Bean
  ListDecisionsUseCase listDecisionsUseCase(
      AutopilotDecisionPort decisions, UnitOfWorkPort unitOfWork) {
    return new ListDecisionsUseCase(decisions, unitOfWork);
  }

  @Bean
  RunAutopilotUseCase runAutopilotUseCase(AutopilotCycle cycle) {
    return new RunAutopilotUseCase(cycle);
  }

  @Bean
  ListLiveValvesUseCase listLiveValvesUseCase(
      AutopilotContextPort context, UnitOfWorkPort unitOfWork) {
    return new ListLiveValvesUseCase(context, unitOfWork);
  }

  @Bean
  AssistantFacade assistantFacade(
      SnapshotBuilder snapshots,
      AutopilotSettingsPort settings,
      AutopilotDecisionPort decisions,
      InsightsPort insights,
      UnitOfWorkPort unitOfWork) {
    return new AssistantFacade(snapshots, settings, decisions, insights, unitOfWork);
  }

  @Bean
  ReportUseCases reportUseCases(
      ReportPort reports,
      AqueductPort aqueducts,
      InsightsPort insights,
      UnitOfWorkPort unitOfWork) {
    return new ReportUseCases(reports, aqueducts, insights, unitOfWork);
  }
}
