package co.caudal.infrastructure.config;

import co.caudal.application.audit.AuditLogCursor;
import co.caudal.application.audit.ListAuditLogUseCase;
import co.caudal.application.audit.ListSecurityEventsUseCase;
import co.caudal.application.audit.SecurityEventCursor;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.AuditLogQueryPort;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RetentionPolicyPort;
import co.caudal.application.port.out.SecurityEventQueryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.retention.UpdateRetentionPolicyCommandHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases that read the audit log and the security events. */
@Configuration(proxyBeanMethods = false)
class AuditServicesConfig {

  @Bean
  AuditLogCursor auditLogCursor(SignedCursorCodec codec) {
    return new AuditLogCursor(codec);
  }

  @Bean
  ListAuditLogUseCase listAuditLogUseCase(
      AuditLogQueryPort auditLog,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      AuditLogCursor cursors,
      UnitOfWorkPort unitOfWork) {
    return new ListAuditLogUseCase(auditLog, accessLog, hasher, cursors, unitOfWork);
  }

  @Bean
  SecurityEventCursor securityEventCursor(SignedCursorCodec codec) {
    return new SecurityEventCursor(codec);
  }

  @Bean
  ListSecurityEventsUseCase listSecurityEventsUseCase(
      SecurityEventQueryPort events,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      SecurityEventCursor cursors,
      UnitOfWorkPort unitOfWork) {
    return new ListSecurityEventsUseCase(events, accessLog, hasher, cursors, unitOfWork);
  }

  @Bean
  UpdateRetentionPolicyCommandHandler updateRetentionPolicyCommandHandler(
      RetentionPolicyPort policies, UnitOfWorkPort unitOfWork) {
    return new UpdateRetentionPolicyCommandHandler(policies, unitOfWork);
  }
}
