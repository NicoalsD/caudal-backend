package co.caudal.application.port.out;

import java.util.Map;
import java.util.UUID;

/** Reads and opens anomalies ({@code ops.anomalies}). Row level security limits the aqueduct. */
public interface AnomalyPort {

  /**
   * Counts the open anomalies of the aqueduct of the session.
   *
   * @return how many are {@code OPEN}
   */
  int countOpen();

  /**
   * Tells whether an open anomaly of a kind already exists for a tank.
   *
   * @param kind the kind, for example {@code STALE_DATA}
   * @param tankId the tank, kept in the evidence
   * @return true if one is open
   */
  boolean openExists(String kind, UUID tankId);

  /**
   * Opens an anomaly.
   *
   * @param aqueductId the aqueduct
   * @param kind the kind
   * @param evidence the facts that raised it, with the {@code tank_id}
   * @return its id
   */
  UUID open(UUID aqueductId, String kind, Map<String, Object> evidence);
}
