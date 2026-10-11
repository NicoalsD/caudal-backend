package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;

/** Lists the memberships of the administrator's aqueduct (docs/API.md, section 3.5). */
public class ListMembershipsUseCase {

  private static final String LIST = "memberships";

  private final MembershipAdminPort memberships;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param memberships memberships
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListMembershipsUseCase(
      MembershipAdminPort memberships, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    this.memberships = memberships;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller the administrator
   * @param userId only this account, or null
   * @param aqueductId only this aqueduct, or null; any other than the session's is forbidden
   * @param limit page size
   * @param cursor cursor of the page, or null for the first
   * @return the page
   * @throws DomainException with {@code FORBIDDEN} for another aqueduct or {@code CURSOR_INVALID}
   */
  public Page<MembershipView> execute(
      Caller caller, UUID userId, UUID aqueductId, int limit, String cursor) {
    if (aqueductId != null && !aqueductId.equals(caller.aqueductId())) {
      throw new DomainException(ErrorCode.FORBIDDEN);
    }
    UUID after = cursor == null ? null : parse(cursors.decode(LIST, cursor));
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<MembershipView> rows =
              memberships.list(caller.aqueductId(), userId, after, limit + 1);
          if (rows.size() <= limit) {
            return new Page<>(rows, null);
          }
          List<MembershipView> items = rows.subList(0, limit);
          return new Page<>(items, cursors.encode(LIST, items.get(limit - 1).id().toString()));
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
