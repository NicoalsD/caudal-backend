package co.caudal.infrastructure.config;

import co.caudal.application.incident.CreateIncidentCommandHandler;
import co.caudal.application.incident.IncidentCursor;
import co.caudal.application.incident.ListIncidentsUseCase;
import co.caudal.application.incident.UpdateIncidentStatusCommandHandler;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.PermissionPort;
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

  @Bean
  CreateIncidentCommandHandler createIncidentCommandHandler(
      CatalogPort catalogs,
      NetworkPort network,
      IncidentPort incidents,
      UnitOfWorkPort unitOfWork) {
    return new CreateIncidentCommandHandler(catalogs, network, incidents, unitOfWork);
  }

  @Bean
  UpdateIncidentStatusCommandHandler updateIncidentStatusCommandHandler(
      IncidentPort incidents, PermissionPort permissions, UnitOfWorkPort unitOfWork) {
    return new UpdateIncidentStatusCommandHandler(incidents, permissions, unitOfWork);
  }
}
