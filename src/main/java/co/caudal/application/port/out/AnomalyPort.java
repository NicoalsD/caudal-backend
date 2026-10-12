package co.caudal.application.port.out;

import co.caudal.application.anomaly.AnomalyView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  /**
   * Finds an anomaly of the aqueduct.
   *
   * @param id the anomaly
   * @return the anomaly, or empty
   */
  Optional<AnomalyView> find(UUID id);

  /**
   * Lists anomalies, newest first (ids are time ordered).
   *
   * @param status only this status, or null
   * @param kind only this kind, or null
   * @param beforeId only ids before this one, or null from the newest
   * @param limit maximum number
   * @return the anomalies
   */
  List<AnomalyView> list(String status, String kind, UUID beforeId, int limit);

  /**
   * Saves the resolution of an anomaly.
   *
   * @param id the anomaly
   * @param status the new status
   * @param resolvedBy who resolved it
   * @param resolvedAt when
   * @param note the note, or null
   * @param relatedIncidentId the incident, or null
   */
  void resolve(
      UUID id,
      String status,
      UUID resolvedBy,
      Instant resolvedAt,
      String note,
      UUID relatedIncidentId);

  /**
   * Tells whether an incident exists in the aqueduct.
   *
   * @param incidentId the incident
   * @return true if it exists
   */
  boolean incidentExists(UUID incidentId);
}
