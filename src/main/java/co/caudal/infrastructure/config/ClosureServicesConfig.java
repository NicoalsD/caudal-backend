package co.caudal.infrastructure.config;

import co.caudal.application.execution.CloseDayCommandHandler;
import co.caudal.application.execution.GetDayClosureUseCase;
import co.caudal.application.execution.RecordExecutionCommandHandler;
import co.caudal.application.port.out.ClosurePort;
import co.caudal.application.port.out.ExecutionPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the execution of shifts, the closure of days and the minutes. */
@Configuration(proxyBeanMethods = false)
class ClosureServicesConfig {

  @Bean
  RecordExecutionCommandHandler recordExecutionCommandHandler(
      ExecutionPort executions, UnitOfWorkPort unitOfWork) {
    return new RecordExecutionCommandHandler(executions, unitOfWork);
  }

  @Bean
  CloseDayCommandHandler closeDayCommandHandler(
      ClosurePort closures, ProposalPort proposals, UnitOfWorkPort unitOfWork) {
    return new CloseDayCommandHandler(closures, proposals, unitOfWork);
  }

  @Bean
  GetDayClosureUseCase getDayClosureUseCase(
      ClosurePort closures, ExecutionPort executions, UnitOfWorkPort unitOfWork) {
    return new GetDayClosureUseCase(closures, executions, unitOfWork);
  }
}
