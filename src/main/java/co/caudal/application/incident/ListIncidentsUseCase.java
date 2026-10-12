package co.caudal.application.incident;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/**
 * Lists the incidents of the aqueduct, the newest report first (docs/API.md, section 12.1). The
 * reporter never appears: the port selects only the columns the Board may read.
 */
public class ListIncidentsUseCase {

  private final IncidentPort incidents;
  private final IncidentCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param incidents incidents
   * @param cursors cursor codec of incidents
   * @param unitOfWork transaction boundary
   */
  public ListIncidentsUseCase(
      IncidentPort incidents, IncidentCursor cursors, UnitOfWorkPort unitOfWork) {
    this.incidents = incidents;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param filter the optional filters
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws DomainException with {@code VALIDATION_ERROR} when {@code from} is after {@code to}, or
   *     {@code CURSOR_INVALID} for an altered cursor
   */
  public Page<IncidentView> execute(
      Caller caller, IncidentFilter filter, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    IncidentCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<IncidentView> rows =
              incidents.list(
                  filter,
                  position == null ? null : position.reportedAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<IncidentView> items = List.copyOf(rows.subList(0, size));
          IncidentView last = items.get(size - 1);
          return new Page<>(items, cursors.encode(last.reportedAt(), last.id()));
        });
  }
}
