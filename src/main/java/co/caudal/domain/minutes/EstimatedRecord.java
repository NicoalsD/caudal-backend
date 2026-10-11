package co.caudal.domain.minutes;

/**
 * Something calculated: a forecast, the hours of a proposal, an evaluation.
 *
 * @param entry the fact
 */
public record EstimatedRecord(MinutesEntry entry) implements EpistemicRecord {

  @Override
  public <R> R accept(MinutesSectionVisitor<R> visitor) {
    return visitor.visitEstimated(this);
  }
}
