package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAdminPort;
import java.util.List;

/** Lists the accounts of the administrator's aqueduct by username (docs/API.md, section 3.1). */
public class ListUsersUseCase {

  private static final String LIST = "users";

  private final UserAdminPort users;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param users accounts of the aqueduct
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListUsersUseCase(
      UserAdminPort users, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    this.users = users;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller the administrator
   * @param status only this status, or null for all
   * @param limit page size
   * @param cursor cursor of the page, or null for the first
   * @return the page
   */
  public Page<ManagedUser> execute(Caller caller, String status, int limit, String cursor) {
    String after = cursor == null ? null : cursors.decode(LIST, cursor);
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<ManagedUser> rows =
              users.listInAqueduct(caller.aqueductId(), status, after, limit + 1);
          if (rows.size() <= limit) {
            return new Page<>(rows, null);
          }
          List<ManagedUser> items = rows.subList(0, limit);
          return new Page<>(items, cursors.encode(LIST, items.get(limit - 1).username()));
        });
  }
}
