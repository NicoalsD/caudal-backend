package co.caudal.application.port.out;

import co.caudal.application.execution.ClosureView;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Closures of days. Row level security limits the aqueduct. */
public interface ClosurePort {

  /**
   * The closure of a day.
   *
   * @param serviceDate the day
   * @return the closure, or empty if the day is open
   */
  Optional<ClosureView> find(LocalDate serviceDate);

  /**
   * Stores the closure of a day.
   *
   * @param aqueductId the aqueduct
   * @param serviceDate the day
   * @param notes the notes, or null
   * @param closedBy who closes it
   * @return the closure
   */
  ClosureView insert(UUID aqueductId, LocalDate serviceDate, String notes, UUID closedBy);
}
