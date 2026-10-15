package co.caudal.infrastructure.config;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.ScheduleHistoryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.schedule.ApproveProposalCommandHandler;
import co.caudal.application.schedule.GenerateProposalCommandHandler;
import co.caudal.application.schedule.GetDayScheduleUseCase;
import co.caudal.application.schedule.GetProposalUseCase;
import co.caudal.application.schedule.ListProposalsUseCase;
import co.caudal.application.schedule.ModifyProposalCommandHandler;
import co.caudal.application.schedule.ProposalDecisions;
import co.caudal.application.schedule.RejectProposalCommandHandler;
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

  @Bean
  ListProposalsUseCase listProposalsUseCase(
      ProposalPort proposals, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    return new ListProposalsUseCase(proposals, cursors, unitOfWork);
  }

  @Bean
  GetProposalUseCase getProposalUseCase(
      ProposalPort proposals, AqueductPort aqueducts, UnitOfWorkPort unitOfWork, Clock clock) {
    return new GetProposalUseCase(proposals, aqueducts, unitOfWork, clock);
  }

  @Bean
  GetDayScheduleUseCase getDayScheduleUseCase(
      ProposalPort proposals,
      PublicationPort publications,
      AqueductPort aqueducts,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new GetDayScheduleUseCase(proposals, publications, aqueducts, unitOfWork, clock);
  }

  @Bean
  ProposalDecisions proposalDecisions(ProposalPort proposals, AqueductPort aqueducts, Clock clock) {
    return new ProposalDecisions(proposals, aqueducts, clock);
  }

  @Bean
  ApproveProposalCommandHandler approveProposalCommandHandler(
      ProposalDecisions decisions, UnitOfWorkPort unitOfWork) {
    return new ApproveProposalCommandHandler(decisions, unitOfWork);
  }

  @Bean
  ModifyProposalCommandHandler modifyProposalCommandHandler(
      ProposalDecisions decisions,
      RuleSetPort ruleSets,
      NetworkPort network,
      ProposalPort proposals,
      UnitOfWorkPort unitOfWork) {
    return new ModifyProposalCommandHandler(decisions, ruleSets, network, proposals, unitOfWork);
  }

  @Bean
  RejectProposalCommandHandler rejectProposalCommandHandler(
      ProposalDecisions decisions, UnitOfWorkPort unitOfWork) {
    return new RejectProposalCommandHandler(decisions, unitOfWork);
  }
}
