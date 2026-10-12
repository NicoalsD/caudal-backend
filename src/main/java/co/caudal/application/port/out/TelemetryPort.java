package co.caudal.application.port.out;

import co.caudal.application.device.telemetry.StoredTelemetryPoint;
import co.caudal.application.device.telemetry.TelemetryPoint;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the telemetry points of the devices. Runs inside a transaction opened with
 * {@link UnitOfWorkPort#executeAs}: the table has row level security.
 */
public interface TelemetryPort {

  /**
   * Finds a point by its id.
   *
   * @param id the point
   * @return the point, or empty if it is not stored in the aqueduct of the session
   */
  Optional<StoredTelemetryPoint> find(UUID id);

  /**
   * Stores a point unless its id is taken. The point is append-only.
   *
   * @param aqueductId the aqueduct of the device
   * @param deviceId the device
   * @param point the point
   * @param readingId the reading created from the point, or null
   * @return true if stored, false if the id already existed
   */
  boolean insert(UUID aqueductId, UUID deviceId, TelemetryPoint point, UUID readingId);
}
