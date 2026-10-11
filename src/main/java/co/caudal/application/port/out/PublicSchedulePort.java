package co.caudal.application.port.out;

import co.caudal.application.publication.PublicSchedule;
import java.time.LocalDate;
import java.util.Optional;

/** Reads the schedule of a day for the public page. */
public interface PublicSchedulePort {

  /**
   * The schedule of a day of the aqueduct of the session.
   *
   * @param day the day of service
   * @return the schedule, or empty when there is nothing to show
   */
  Optional<PublicSchedule> find(LocalDate day);
}
