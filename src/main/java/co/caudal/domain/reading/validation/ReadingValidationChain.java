package co.caudal.domain.reading.validation;

import java.util.ArrayList;
import java.util.List;

/** Builds the chain of checks of a reading in the order of docs/Reglas-de-negocio.md, section 8. */
public final class ReadingValidationChain {

  private ReadingValidationChain() {}

  /**
   * The standard chain: range, moment, duplicate and age.
   *
   * @param history the earlier readings of the tank
   * @return the first link
   */
  public static ReadingValidationHandler standard(ReadingHistory history) {
    ReadingValidationHandler first = new GaugeRangeHandler();
    first
        .linkWith(new TimestampHandler())
        .linkWith(new DuplicateHandler(history))
        .linkWith(new AgeHandler());
    return first;
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
