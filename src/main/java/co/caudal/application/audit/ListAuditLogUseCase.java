package co.caudal.application.audit;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.AuditLogQueryPort;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/**
 * Reads the audit log of the caller's aqueduct (docs/API.md, section 18.1). Every query is written
 * to {@code audit.data_access_log} with the resource {@code AUDIT_LOG}, and the states go through
 * {@link AuditStateRedactor}.
 */
public class ListAuditLogUseCase {

  /** Resource name of the access log for the audit log. */
  static final String RESOURCE = "AUDIT_LOG";

  private final AuditLogQueryPort auditLog;
  private final DataAccessLogPort accessLog;
  private final PrivacyHasherPort hasher;
  private final AuditLogCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param auditLog the audit log
   * @param accessLog the access log of sensitive reads
   * @param hasher hides the address of the caller
   * @param cursors cursor codec of the audit log
   * @param unitOfWork transaction boundary
   */
  public ListAuditLogUseCase(
      AuditLogQueryPort auditLog,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      AuditLogCursor cursors,
      UnitOfWorkPort unitOfWork) {
    this.auditLog = auditLog;
    this.accessLog = accessLog;
    this.hasher = hasher;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page of the audit log.
   *
   * @param caller who asks
   * @param filter the optional filters
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws DomainException with {@code VALIDATION_ERROR} when {@code from} is after {@code to}, or
   *     {@code CURSOR_INVALID} for an altered cursor
   */
  public Page<AuditLogView> execute(
      Caller caller, AuditLogFilter filter, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    AuditLogCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          accessLog.record(
              caller.aqueductId(),
              caller.userId(),
              RESOURCE,
              null,
              caller.ipAddress() == null ? null : hasher.hmac(caller.ipAddress()));
          List<AuditLogView> rows =
              auditLog.list(
                  caller.aqueductId(),
                  filter,
                  position == null ? null : position.occurredAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page<>(redacted(rows), null);
          }
          List<AuditLogView> items = rows.subList(0, size);
          AuditLogView last = items.get(size - 1);
          return new Page<>(redacted(items), cursors.encode(last.occurredAt(), last.id()));
        });
  }

  private static List<AuditLogView> redacted(List<AuditLogView> rows) {
    return rows.stream()
        .map(
            row ->
                row.withStates(
                    AuditStateRedactor.redact(row.beforeState()),
                    AuditStateRedactor.redact(row.afterState())))
        .toList();
  }
}
