package co.caudal.application.reading;

import java.util.UUID;

/**
 * Where a stored reading came from.
 *
 * @param source {@code MANUAL_APP}, {@code SENSOR} or {@code IMPORT}
 * @param syncBatchId the offline batch it came in, or null
 * @param importBatchId the import of simulated history it came in, or null
 * @param deviceId the sensor that sent it, or null
 */
public record ReadingOrigin(String source, UUID syncBatchId, UUID importBatchId, UUID deviceId) {

  /** Source of the readings sent by a level sensor. */
  public static final String SENSOR = "SENSOR";

  /**
   * An origin with no device, the form used by the app and the import.
   *
   * @param source the source
   * @param syncBatchId the offline batch, or null
   * @param importBatchId the import, or null
   */
  public ReadingOrigin(String source, UUID syncBatchId, UUID importBatchId) {
    this(source, syncBatchId, importBatchId, null);
  }

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

  /**
   * A reading sent by a level sensor. It has no person behind it.
   *
   * @param deviceId the sensor
   * @return the origin
   */
  public static ReadingOrigin sensor(UUID deviceId) {
    return new ReadingOrigin(SENSOR, null, null, deviceId);
  }
}
