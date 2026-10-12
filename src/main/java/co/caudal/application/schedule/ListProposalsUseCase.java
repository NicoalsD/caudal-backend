package co.caudal.application.schedule;

import co.caudal.application.auth.Caller;
import co.caudal.application.pagination.Page;
import co.caudal.application.pagination.PageLimit;
import co.caudal.application.pagination.SignedCursorCodec;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lists the proposals of the aqueduct, the newest first (docs/API.md, section 10.2). */
public class ListProposalsUseCase {

  private static final String LIST = "schedule-proposals";

  private final ProposalPort proposals;
  private final SignedCursorCodec cursors;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param proposals proposals
   * @param cursors cursor codec
   * @param unitOfWork transaction boundary
   */
  public ListProposalsUseCase(
      ProposalPort proposals, SignedCursorCodec cursors, UnitOfWorkPort unitOfWork) {
    this.proposals = proposals;
    this.cursors = cursors;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a page.
   *
   * @param caller who asks
   * @param from first day of service, or null
   * @param to last day of service, or null
   * @param status only this status, or null
   * @param limit page size
   * @param cursor cursor of the page, or null
   * @return the page
   */
  public Page<ProposalSummary> execute(
      Caller caller, LocalDate from, LocalDate to, String status, int limit, String cursor) {
    int size = PageLimit.checked(limit);
    if (from != null && to != null && from.isAfter(to)) {
      throw new FieldValidationException("service_date_to", "FIELD_VALUE_NOT_ALLOWED");
    }
    UUID before = cursor == null ? null : parse(cursors.decode(LIST, cursor));
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<ProposalSummary> rows = proposals.list(from, to, status, before, size + 1);
          if (rows.size() <= size) {
            return new Page<>(rows, null);
          }
          List<ProposalSummary> items = rows.subList(0, size);
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
