package co.caudal.domain.minutes;

/**
 * Something suspected: an open or dismissed anomaly, a flagged reading.
 *
 * @param entry the fact
 */
public record InferredRecord(MinutesEntry entry) implements EpistemicRecord {

  @Override
  public <R> R accept(MinutesSectionVisitor<R> visitor) {
    return visitor.visitInferred(this);
  }
}
