package co.caudal.application.port.out;

import co.caudal.application.incident.NewIncident;
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
}
