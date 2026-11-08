package co.caudal.application.port.out;

import co.caudal.domain.autopilot.AutopilotMode;
import co.caudal.domain.autopilot.AutopilotSettings;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads and writes {@code org.autopilot_settings}. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the table has row level security.
 */
public interface AutopilotSettingsPort {

  /**
   * Reads the settings of an aqueduct; if it has none yet, creates them with the defaults of the
   * columns of the table.
   *
   * @param aqueductId the aqueduct of the session
   * @return the settings
   */
  AutopilotSettings getOrCreate(UUID aqueductId);

  /**
   * Replaces the settings of an aqueduct.
   *
   * @param aqueductId the aqueduct of the session
   * @param mode the mode
   * @param operatorAbsenceMinutes minutes without human action before AUTOMATIC acts
   * @param reserveFloorPct the reserve floor, in percent
   * @param updatedBy who changed them
   * @return the stored settings
   */
  AutopilotSettings save(
      UUID aqueductId,
      AutopilotMode mode,
      int operatorAbsenceMinutes,
      BigDecimal reserveFloorPct,
      UUID updatedBy);
}
