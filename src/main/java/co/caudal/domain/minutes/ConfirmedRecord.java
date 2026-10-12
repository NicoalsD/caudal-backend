package co.caudal.domain.minutes;

/**
 * Something checked in the field: an execution, a confirmed or resolved incident.
 *
 * @param entry the fact
 */
public record ConfirmedRecord(MinutesEntry entry) implements EpistemicRecord {

  @Override
  public <R> R accept(MinutesSectionVisitor<R> visitor) {
    return visitor.visitConfirmed(this);
  }
}
