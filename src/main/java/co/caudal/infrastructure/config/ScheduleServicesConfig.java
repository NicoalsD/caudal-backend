package co.caudal.infrastructure.config;

import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.ScheduleHistoryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.schedule.GenerateProposalCommandHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the proposals of shifts and the decisions of the Board. */
@Configuration(proxyBeanMethods = false)
class ScheduleServicesConfig {

  @Bean
  GenerateProposalCommandHandler.ScheduleData scheduleData(
      RuleSetPort ruleSets,
      NetworkPort network,
      ReadingPort readings,
      ForecastRunPort forecasts,
      ScheduleHistoryPort history,
      AqueductPort aqueducts) {
    return new GenerateProposalCommandHandler.ScheduleData(
        ruleSets, network, readings, forecasts, history, aqueducts);
  }

  @Bean
  GenerateProposalCommandHandler generateProposalCommandHandler(
      GenerateProposalCommandHandler.ScheduleData data,
      ProposalPort proposals,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new GenerateProposalCommandHandler(data, proposals, unitOfWork, clock);
  }
}
