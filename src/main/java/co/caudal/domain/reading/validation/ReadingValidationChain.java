package co.caudal.domain.reading.validation;

import java.util.ArrayList;
import java.util.List;

/** Builds the chain of checks of a reading in the order of docs/Reglas-de-negocio.md, section 8. */
public final class ReadingValidationChain {

  private ReadingValidationChain() {}

  /**
   * The standard chain.
   *
   * @return the first link
   */
  public static ReadingValidationHandler standard() {
    return new GaugeRangeHandler();
  }

  /**
   * Runs a chain and returns the warnings.
   *
   * @param chain the first link
   * @param candidate the reading
   * @return the warnings, empty when the reading is clean
   */
  public static List<ReadingIssue> run(ReadingValidationHandler chain, ReadingCandidate candidate) {
    List<ReadingIssue> issues = new ArrayList<>();
    chain.validate(candidate, issues);
    return List.copyOf(issues);
  }
}
