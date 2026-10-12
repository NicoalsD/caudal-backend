package co.caudal.application.device.telemetry;

import java.util.List;

/**
 * The answer to a batch of telemetry: one result per point, in the order received.
 *
 * @param results the results
 */
public record TelemetryBatchResult(List<TelemetryPointResult> results) {

  /** Copies the results. */
  public TelemetryBatchResult {
    results = List.copyOf(results);
  }

  /**
   * Points stored now.
   *
   * @return the count
   */
  public long accepted() {
    return count(TelemetryPointResult.ACCEPTED);
  }

  /**
   * Points that were already stored with the same content.
   *
   * @return the count
   */
  public long duplicates() {
    return count(TelemetryPointResult.DUPLICATE);
  }

  /**
   * Points that were not stored.
   *
   * @return the count
   */
  public long rejected() {
    return count(TelemetryPointResult.REJECTED);
  }

  private long count(String status) {
    return results.stream().filter(result -> result.status().equals(status)).count();
  }
}
