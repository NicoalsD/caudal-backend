package co.caudal.application.port.out;

import co.caudal.application.incident.NewIncident;
import co.caudal.application.incident.TrackedReport;
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
}
