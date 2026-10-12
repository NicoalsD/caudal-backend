package co.caudal.application.reading;

import java.math.BigDecimal;
import java.util.List;

/**
 * One reading with its warnings and its corrections (docs/API.md, section 7.4).
 *
 * @param reading the reading as it was observed
 * @param effectiveGaugeValue the value that counts: the latest correction, or the original
 * @param issues its warnings
 * @param corrections its corrections, oldest first
 */
public record ReadingDetail(
    ReadingView reading,
    BigDecimal effectiveGaugeValue,
    List<IssueView> issues,
    List<CorrectionView> corrections) {

  /** Copies the lists. */
  public ReadingDetail {
    issues = List.copyOf(issues);
    corrections = List.copyOf(corrections);
  }
}
