package co.caudal.application.port.out;

import co.caudal.application.incident.IncidentFilter;
import co.caudal.application.incident.IncidentView;
import co.caudal.application.incident.NewIncident;
import co.caudal.application.incident.TrackedReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Incidents and their history. Row level security limits the aqueduct. */
public interface IncidentPort {

  /**
   * Stores a new incident in status {@code REPORTED} with the first row of its history.
   *
   * @param aqueductId the aqueduct
   * @param incident the incident
   * @return its id
   */
  UUID insert(UUID aqueductId, NewIncident incident);

  /**
   * Finds a report by the hash of its tracking code, in any aqueduct.
   *
   * @param trackingCodeHash the hash
   * @return only what the public may see, or empty
   */
  Optional<TrackedReport> findByTrackingHash(String trackingCodeHash);

  /**
   * Lists incidents, the newest report first. Only the columns of {@link IncidentView} are read.
   *
   * @param filter the optional filters
   * @param before only incidents reported before this moment, or null from the newest
   * @param beforeId with {@code before}: only ids before this one, to break ties
   * @param limit maximum number
   * @return the incidents
   */
  List<IncidentView> list(IncidentFilter filter, Instant before, UUID beforeId, int limit);
}
