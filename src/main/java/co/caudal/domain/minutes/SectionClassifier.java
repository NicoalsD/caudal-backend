package co.caudal.domain.minutes;

/**
 * Tells the section each record goes to.
 *
 * @pattern P21 Visitor
 */
public final class SectionClassifier implements MinutesSectionVisitor<MinutesSection> {

  @Override
  public MinutesSection visitObserved(ObservedRecord record) {
    return MinutesSection.OBSERVED;
  }

  @Override
  public MinutesSection visitEstimated(EstimatedRecord record) {
    return MinutesSection.ESTIMATED;
  }

  @Override
  public MinutesSection visitInferred(InferredRecord record) {
    return MinutesSection.INFERRED;
  }

  @Override
  public MinutesSection visitConfirmed(ConfirmedRecord record) {
    return MinutesSection.CONFIRMED;
  }
}
