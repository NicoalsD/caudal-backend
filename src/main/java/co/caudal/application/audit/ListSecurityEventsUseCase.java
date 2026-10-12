package co.caudal.application.audit;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.DataAccessLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventQueryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/**
 * Reads the security events of the caller's aqueduct (docs/API.md, section 18.2). The events of
 * other aqueducts and the global ones (no aqueduct) are never returned: the table has no row level
 * security, so the query filters by aqueduct itself. Each read is written to the access log with
 * the resource {@code SECURITY_EVENTS}, because the details are confidential.
 */
public class ListSecurityEventsUseCase {

  /** Resource name of the access log for the security events. */
  static final String RESOURCE = "SECURITY_EVENTS";

  private final SecurityEventQueryPort events;
  private final DataAccessLogPort accessLog;
  private final PrivacyHasherPort hasher;
  private final SecurityEventCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param events security events
   * @param accessLog the access log of sensitive reads
   * @param hasher hides the address of the caller
   * @param cursors cursor codec of the security events
   * @param unitOfWork transaction boundary
   */
  public ListSecurityEventsUseCase(
      SecurityEventQueryPort events,
      DataAccessLogPort accessLog,
      PrivacyHasherPort hasher,
      SecurityEventCursor cursors,
      UnitOfWorkPort unitOfWork) {
    this.events = events;
    this.accessLog = accessLog;
    this.hasher = hasher;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page of security events.
   *
   * @param caller who asks
   * @param filter the optional filters
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws DomainException with {@code VALIDATION_ERROR} when {@code from} is after {@code to}, or
   *     {@code CURSOR_INVALID} for an altered cursor
   */
  public Page<SecurityEventView> execute(
      Caller caller, SecurityEventFilter filter, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    SecurityEventCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          accessLog.record(
              caller.aqueductId(),
              caller.userId(),
              RESOURCE,
              null,
              caller.ipAddress() == null ? null : hasher.hmac(caller.ipAddress()));
          List<SecurityEventView> rows =
              events.list(
                  caller.aqueductId(),
                  filter,
                  position == null ? null : position.createdAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page<>(redacted(rows), null);
          }
          List<SecurityEventView> items = rows.subList(0, size);
          SecurityEventView last = items.get(size - 1);
          return new Page<>(redacted(items), cursors.encode(last.createdAt(), last.id()));
        });
  }

  private static List<SecurityEventView> redacted(List<SecurityEventView> rows) {
    return rows.stream()
        .map(row -> row.withDetails(AuditStateRedactor.redact(row.details())))
        .toList();
  }
}
