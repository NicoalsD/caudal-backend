package co.caudal.infrastructure.config;

import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.MinutesSourcePort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.summary.CreateSummaryShareCommandHandler;
import co.caudal.application.summary.ListSummariesUseCase;
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

  @Bean
  @SuppressWarnings("checkstyle:ParameterNumber")
  ListSummariesUseCase listSummariesUseCase(
      SummarySharePort shares,
      MembershipPort memberships,
      MinutesPort finalMinutes,
      MinutesSourcePort sources,
      AqueductPort aqueducts,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      SignedCursorCodec cursors,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ListSummariesUseCase(
        shares,
        memberships,
        finalMinutes,
        sources,
        aqueducts,
        accessLog,
        hasher,
        cursors,
        unitOfWork,
        clock);
  }
}
