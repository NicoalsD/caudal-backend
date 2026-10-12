package co.caudal.application.reading;

import java.util.List;
import java.util.UUID;

/**
 * The result of an offline batch (docs/API.md, section 7.2).
 *
 * @param syncBatchId the id chosen by the client
 * @param itemsReceived readings it had
 * @param itemsAccepted readings stored or recognized as retries
 * @param itemsRejected readings refused
 * @param replay true if the same batch had already been received
 * @param results one result per reading, in the order they were sent
 */
public record BatchResult(
    UUID syncBatchId,
    int itemsReceived,
    int itemsAccepted,
    int itemsRejected,
    boolean replay,
    List<BatchItemResult> results) {

  /** Copies the results. */
  public BatchResult {
    results = List.copyOf(results);
  }
}
