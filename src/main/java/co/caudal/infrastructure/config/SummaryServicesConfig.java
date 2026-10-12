package co.caudal.infrastructure.config;

import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.summary.CreateSummaryShareCommandHandler;
import co.caudal.application.summary.RevokeSummaryShareCommandHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the authorizations of summaries for entities. */
@Configuration(proxyBeanMethods = false)
class SummaryServicesConfig {

  @Bean
  CreateSummaryShareCommandHandler createSummaryShareCommandHandler(
      SummarySharePort shares, MembershipPort memberships, UnitOfWorkPort unitOfWork, Clock clock) {
    return new CreateSummaryShareCommandHandler(shares, memberships, unitOfWork, clock);
  }

  @Bean
  RevokeSummaryShareCommandHandler revokeSummaryShareCommandHandler(
      SummarySharePort shares, UnitOfWorkPort unitOfWork, Clock clock) {
    return new RevokeSummaryShareCommandHandler(shares, unitOfWork, clock);
  }
}
