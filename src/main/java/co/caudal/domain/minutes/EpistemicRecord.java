package co.caudal.domain.minutes;

/**
 * A fact of the period with how sure it is. Sealed, so every visitor handles every kind and a new
 * kind forces every visitor to change.
 *
 * @pattern P21 Visitor
 */
public sealed interface EpistemicRecord
    permits ObservedRecord, EstimatedRecord, InferredRecord, ConfirmedRecord {

  /**
   * The fact.
   *
   * @return the entry
   */
  MinutesEntry entry();

  /**
   * Lets a visitor act on the concrete kind.
   *
   * @param visitor the visitor
   * @param <R> what it returns
   * @return what the visitor returns
   */
  <R> R accept(MinutesSectionVisitor<R> visitor);
}
