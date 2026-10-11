package co.caudal.application.port.out;

import co.caudal.application.minutes.MinutesView;
import co.caudal.domain.minutes.Minutes;
import java.util.Optional;
import java.util.UUID;

/** Minutes of the Board. Row level security limits the aqueduct. */
public interface MinutesPort {

  /**
   * Stores new minutes as a draft.
   *
   * @param aqueductId the aqueduct
   * @param minutes the content
   * @param generatedBy who generated them
   * @return their id
   */
  UUID insert(UUID aqueductId, Minutes minutes, UUID generatedBy);

  /**
   * Reads minutes.
   *
   * @param id the minutes
   * @return the minutes, or empty
   */
  Optional<MinutesView> find(UUID id);
}
