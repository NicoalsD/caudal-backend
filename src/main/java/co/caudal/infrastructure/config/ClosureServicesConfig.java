package co.caudal.infrastructure.config;

import co.caudal.application.execution.RecordExecutionCommandHandler;
import co.caudal.application.port.out.ExecutionPort;
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
}
