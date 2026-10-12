package co.caudal.application.port.out;

import co.caudal.application.schedule.ServiceEnd;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tells when each sector last had water. Row level security limits the aqueduct. */
public interface ScheduleHistoryPort {

  /**
   * The end of the last service of a sector before a moment: the last execution {@code COMPLETED}
   * or {@code PARTIAL} not replaced by another, or else the last shift of a published schedule.
   *
   * @param sectorId the sector
   * @param before the reference moment
   * @return the end, or empty if the sector never had a shift
   */
  Optional<ServiceEnd> lastServiceEnd(UUID sectorId, Instant before);
}
