package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/** Lists the readings of a tank, newest first, with a signed cursor. */
public class ListReadingsUseCase {

  /**
   * A page of readings.
   *
   * @param items the readings
   * @param nextCursor cursor of the next page, or null if there are no more
   */
  public record Page(List<ListedReading> items, String nextCursor) {}

  private final NetworkPort network;
  private final ReadingPort readings;
  private final ReadingCursor cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param network tanks
   * @param readings readings
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListReadingsUseCase(
      NetworkPort network, ReadingPort readings, ReadingCursor cursors, UnitOfWorkPort unitOfWork) {
    this.network = network;
    this.readings = readings;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param filter the tank and the optional range and status
   * @param limit page size
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws DomainException with {@code NOT_FOUND} for an unknown tank, {@code CURSOR_INVALID} or
   *     {@code VALIDATION_ERROR} when {@code from} is after {@code to}
   */
  public Page execute(Caller caller, ReadingFilter filter, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    ReadingCursor.Position position = cursor == null ? null : cursors.decode(cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          network.tank(filter.tankId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          List<ListedReading> rows =
              readings.list(
                  filter,
                  position == null ? null : position.observedAt(),
                  position == null ? null : position.id(),
                  size + 1);
          if (rows.size() <= size) {
            return new Page(rows, null);
          }
          List<ListedReading> items = rows.subList(0, size);
          ReadingView last = items.get(size - 1).reading();
          return new Page(List.copyOf(items), cursors.encode(last.observedAt(), last.id()));
        });
  }
}
