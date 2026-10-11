package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/** Lists the versions of the rules of the aqueduct, newest first (docs/API.md, section 6.2). */
public class ListRuleSetsUseCase {

  private static final String LIST = "rule-sets";

  private final RuleSetPort ruleSets;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param ruleSets versions of the rules
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListRuleSetsUseCase(
      RuleSetPort ruleSets, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param status only this status, or null for all
   * @param limit page size
   * @param cursor cursor of the page, or null for the first
   * @return the page
   */
  public Page<RuleSetSummary> execute(Caller caller, String status, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    Integer before = cursor == null ? null : parse(cursors.decode(LIST, cursor));
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<RuleSetSummary> rows = ruleSets.list(status, before, size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<RuleSetSummary> items = rows.subList(0, size);
          return new Page<>(
              items, cursors.encode(LIST, Integer.toString(items.get(size - 1).version())));
        });
  }

  private static Integer parse(String position) {
    try {
      return Integer.valueOf(position);
    } catch (NumberFormatException bad) {
      throw new DomainException(ErrorCode.CURSOR_INVALID);
    }
  }
}
