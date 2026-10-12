package co.caudal.domain.minutes;

/**
 * An operation over the four kinds of records of the minutes.
 *
 * @param <R> what it returns
 * @pattern P21 Visitor
 */
public interface MinutesSectionVisitor<R> {

  /**
   * Acts on an observed record.
   *
   * @param record the record
   * @return the result
   */
  R visitObserved(ObservedRecord record);

  /**
   * Acts on an estimated record.
   *
   * @param record the record
   * @return the result
   */
  R visitEstimated(EstimatedRecord record);

  /**
   * Acts on an inferred record.
   *
   * @param record the record
   * @return the result
   */
  R visitInferred(InferredRecord record);

  /**
   * Acts on a confirmed record.
   *
   * @param record the record
   * @return the result
   */
  R visitConfirmed(ConfirmedRecord record);
}
