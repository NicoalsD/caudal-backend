package co.caudal.domain.minutes;

/**
 * Something someone saw or measured: a valid reading, a fresh damage report, a correction.
 *
 * @param entry the fact
 */
public record ObservedRecord(MinutesEntry entry) implements EpistemicRecord {

  @Override
  public <R> R accept(MinutesSectionVisitor<R> visitor) {
    return visitor.visitObserved(this);
  }
}
