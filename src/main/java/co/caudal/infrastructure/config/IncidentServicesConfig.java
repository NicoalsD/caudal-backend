package co.caudal.infrastructure.config;

import co.caudal.application.incident.IncidentCursor;
import co.caudal.application.incident.ListIncidentsUseCase;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the incidents of the aqueduct. */
@Configuration(proxyBeanMethods = false)
class IncidentServicesConfig {

  @Bean
  IncidentCursor incidentCursor(SignedCursorCodec codec) {
    return new IncidentCursor(codec);
  }

  @Bean
  ListIncidentsUseCase listIncidentsUseCase(
      IncidentPort incidents, IncidentCursor cursors, UnitOfWorkPort unitOfWork) {
    return new ListIncidentsUseCase(incidents, cursors, unitOfWork);
  }
}
