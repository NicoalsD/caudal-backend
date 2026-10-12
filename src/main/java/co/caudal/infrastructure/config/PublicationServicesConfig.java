package co.caudal.infrastructure.config;

import co.caudal.application.device.command.ValveCommandScheduler;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.incident.DamageReportCommandHandler;
import co.caudal.application.incident.PublicFormGuard;
import co.caudal.application.incident.TrackDamageReportUseCase;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.PublicSchedulePort;
import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.publication.GetPublicScheduleUseCase;
import co.caudal.application.publication.PublishScheduleCommandHandler;
import co.caudal.application.publication.SchedulePublicationAuditor;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.infrastructure.document.DocumentRenderingAdapter;
import co.caudal.infrastructure.persistence.PublicSchedulePersistenceAdapter;
import co.caudal.infrastructure.publication.PublicScheduleProxy;
import co.caudal.infrastructure.ratelimit.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Creates the use cases of the publication of schedules and the public page and form. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicFormProperties.class)
class PublicationServicesConfig {

  @Bean
  @SuppressWarnings("checkstyle:ParameterNumber")
  PublishScheduleCommandHandler publishScheduleCommandHandler(
      ProposalPort proposals,
      PublicationPort publications,
      NetworkPort network,
      AqueductPort aqueducts,
      DocumentRenderingPort documents,
      UnitOfWorkPort unitOfWork,
      DomainEventPublisher events,
      ValveCommandScheduler valveCommands,
      Clock clock) {
    return new PublishScheduleCommandHandler(
        proposals,
        publications,
        network,
        aqueducts,
        documents,
        unitOfWork,
        events,
        valveCommands,
        clock);
  }

  @Bean
  SchedulePublicationAuditor schedulePublicationAuditor(AuditLogPort auditLog) {
    return new SchedulePublicationAuditor(auditLog);
  }

  @Bean
  PublicSchedulePort publicSchedulePort(JdbcClient jdbc) {
    return new PublicScheduleProxy(new PublicSchedulePersistenceAdapter(jdbc));
  }

  @Bean
  GetPublicScheduleUseCase getPublicScheduleUseCase(
      AqueductPort aqueducts,
      PublicSchedulePort schedules,
      DocumentRenderingPort documents,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new GetPublicScheduleUseCase(aqueducts, schedules, documents, unitOfWork, clock);
  }

  @Bean
  DocumentRenderingPort documentRenderingPort() {
    return new DocumentRenderingAdapter();
  }

  @Bean
  DamageReportCommandHandler damageReportCommandHandler(
      AqueductPort aqueducts,
      CatalogPort catalogs,
      NetworkPort network,
      IncidentPort incidents,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      PublicFormGuard guard) {
    return new DamageReportCommandHandler(
        aqueducts, catalogs, network, incidents, hasher, unitOfWork, guard);
  }

  @Bean
  PublicFormGuard publicFormGuard(
      RateLimiterPort rateLimiter,
      SecurityEventPort events,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      Clock clock,
      RateLimitProperties rates,
      PublicFormProperties form) {
    return new PublicFormGuard(
        rateLimiter,
        events,
        hasher,
        unitOfWork,
        clock,
        new RateLimit(rates.publicReportsPerHour(), Duration.ofHours(1)),
        Duration.ofSeconds(form.minSeconds()));
  }

  @Bean
  TrackDamageReportUseCase trackDamageReportUseCase(
      IncidentPort incidents, CatalogPort catalogs, UnitOfWorkPort unitOfWork) {
    return new TrackDamageReportUseCase(incidents, catalogs, unitOfWork);
  }
}
