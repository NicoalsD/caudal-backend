package co.caudal.application.minutes;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.util.List;

/**
 * Lists the minutes of the aqueduct, the newest first (docs/API.md, section 13.8). The sections are
 * not read: the port selects only the columns of {@link MinutesSummaryView}.
 */
public class ListMinutesUseCase {

  private final MinutesPort minutes;
  private final MinutesCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param minutes minutes
   * @param cursors cursor codec of minutes
   * @param unitOfWork transaction boundary
   */
  public ListMinutesUseCase(MinutesPort minutes, MinutesCursor cursors, UnitOfWorkPort unitOfWork) {
    this.minutes = minutes;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param limit page size, from 1 to 100
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws co.caudal.shared.error.DomainException with {@code VALIDATION_ERROR} when the size is
   *     out of range, or {@code CURSOR_INVALID} for an altered cursor
   */
  public Page<MinutesSummaryView> execute(Caller caller, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    MinutesCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<MinutesSummaryView> rows =
              minutes.list(
                  position == null ? null : position.generatedAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<MinutesSummaryView> items = List.copyOf(rows.subList(0, size));
          MinutesSummaryView last = items.get(size - 1);
          return new Page<>(items, cursors.encode(last.generatedAt(), last.id()));
        });
  }
}
