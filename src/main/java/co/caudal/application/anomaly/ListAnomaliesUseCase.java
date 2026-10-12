package co.caudal.application.anomaly;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.AnomalyPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;

/** Lists the anomalies of the aqueduct, newest first (docs/API.md, section 9.1). */
public class ListAnomaliesUseCase {

  private static final String LIST = "anomalies";

  private final AnomalyPort anomalies;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param anomalies anomalies
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListAnomaliesUseCase(
      AnomalyPort anomalies, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    this.anomalies = anomalies;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param status only this status, or null
   * @param kind only this kind, or null
   * @param limit page size
   * @param cursor cursor of the page, or null
   * @return the page
   */
  public Page<AnomalyView> execute(
      Caller caller, String status, String kind, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    UUID before = cursor == null ? null : parse(cursors.decode(LIST, cursor));
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<AnomalyView> rows = anomalies.list(status, kind, before, size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<AnomalyView> items = rows.subList(0, size);
          return new Page<>(items, cursors.encode(LIST, items.get(size - 1).id().toString()));
        });
  }

  private static UUID parse(String position) {
    try {
      return UUID.fromString(position);
    } catch (IllegalArgumentException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
