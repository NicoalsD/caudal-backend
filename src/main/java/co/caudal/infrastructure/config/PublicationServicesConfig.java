package co.caudal.infrastructure.config;

import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.publication.PublishScheduleCommandHandler;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the publication of schedules and the public page. */
@Configuration(proxyBeanMethods = false)
class PublicationServicesConfig {

  @Bean
  PublishScheduleCommandHandler publishScheduleCommandHandler(
      ProposalPort proposals,
      PublicationPort publications,
      NetworkPort network,
      AqueductPort aqueducts,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new PublishScheduleCommandHandler(
        proposals, publications, network, aqueducts, unitOfWork, clock);
  }
}
