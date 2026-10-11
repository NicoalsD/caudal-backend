package co.caudal.application.pagination;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * Checks the size of a page again inside the use case, so a caller that skipped the API validation
 * can never ask for a page outside {@link FieldLimits#PAGE_LIMIT_MIN} to {@link
 * FieldLimits#PAGE_LIMIT_MAX}.
 */
public final class PageLimit {

  private PageLimit() {}

  /**
   * Returns the limit if it is inside the allowed range.
   *
   * @param limit the requested size
   * @return the same size
   * @throws DomainException with {@code VALIDATION_ERROR} if it is outside the range
   */
  public static int checked(int limit) {
    if (limit < FieldLimits.PAGE_LIMIT_MIN || limit > FieldLimits.PAGE_LIMIT_MAX) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    return limit;
  }
}
