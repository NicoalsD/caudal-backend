package co.caudal.infrastructure.config;

import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.event.DomainEventSubscriber;
import co.caudal.application.event.SimpleDomainEventPublisher;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.application.rules.RuleSetActivationAuditor;
import co.caudal.domain.event.DomainEvent;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers every {@link DomainEventSubscriber} in the domain event publisher (P17). */
@Configuration(proxyBeanMethods = false)
class DomainEventConfig {

  @Bean
  RuleSetActivationAuditor ruleSetActivationAuditor(AuditLogPort auditLog) {
    return new RuleSetActivationAuditor(auditLog);
  }

  @Bean
  DomainEventPublisher domainEventPublisher(
      List<DomainEventSubscriber<? extends DomainEvent>> subscribers) {
    return new SimpleDomainEventPublisher(subscribers);
  }
}
