package co.caudal.application.reading;

import java.util.UUID;

/**
 * Where a stored reading came from.
 *
 * @param source {@code MANUAL_APP}, {@code SENSOR} or {@code IMPORT}
 * @param syncBatchId the offline batch it came in, or null
 * @param importBatchId the import of simulated history it came in, or null
 */
public record ReadingOrigin(String source, UUID syncBatchId, UUID importBatchId) {

  /** Source of the readings typed in the app. */
  public static final String MANUAL_APP = "MANUAL_APP";

  /** Source of the simulated history imported into a demo aqueduct. */
  public static final String IMPORT = "IMPORT";

  /**
   * A reading typed in the app, alone.
   *
   * @return the origin
   */
  public static ReadingOrigin manual() {
    return new ReadingOrigin(MANUAL_APP, null, null);
  }

  /**
   * A reading typed in the app and sent in an offline batch.
   *
   * @param syncBatchId the batch
   * @return the origin
   */
  public static ReadingOrigin offlineBatch(UUID syncBatchId) {
    return new ReadingOrigin(MANUAL_APP, syncBatchId, null);
  }

  /**
   * A reading of simulated history.
   *
   * @param importBatchId the import
   * @return the origin
   */
  public static ReadingOrigin imported(UUID importBatchId) {
    return new ReadingOrigin(IMPORT, null, importBatchId);
  }
}
