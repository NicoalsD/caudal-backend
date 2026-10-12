package co.caudal.domain.reading.validation;

import co.caudal.shared.error.DomainException;
import java.util.List;
import java.util.Objects;

/**
 * One link of the chain that judges a reading (docs/Reglas-de-negocio.md, section 8). Each link
 * either rejects the reading, by throwing a {@link DomainException} that stops the chain, or adds a
 * warning and passes the reading on; the warnings add up.
 *
 * @pattern P12 Chain of Responsibility
 */
public abstract class ReadingValidationHandler {

  private ReadingValidationHandler next;

  /**
   * Puts a link after this one.
   *
   * @param next the next link
   * @return the next link, to keep chaining
   */
  public final ReadingValidationHandler linkWith(ReadingValidationHandler next) {
    this.next = Objects.requireNonNull(next, "next");
    return next;
  }

  /**
   * Judges a reading with this link and the ones after it.
   *
   * @param candidate the reading
   * @param issues the warnings found so far; links add theirs
   * @throws DomainException if a link rejects the reading
   */
  public final void validate(ReadingCandidate candidate, List<ReadingIssue> issues) {
    check(candidate, issues);
    if (next != null) {
      next.validate(candidate, issues);
    }
  }

  /**
   * The check of this link.
   *
   * @param candidate the reading
   * @param issues where to add a warning
   * @throws DomainException to reject the reading
   */
  protected abstract void check(ReadingCandidate candidate, List<ReadingIssue> issues);
}
