package co.caudal.application.port.out;

import co.caudal.application.simulation.IncidentRow;
import co.caudal.application.simulation.ShiftExecutionRow;
import java.util.UUID;

/** Stores imported simulated history other than readings: shift executions and incidents. */
public interface SimulatedHistoryPort {

  /**
   * Tells whether a shift of the schedule exists in the aqueduct of the session.
   *
   * @param scheduleItemId the shift
   * @return true if it exists
   */
  boolean scheduleItemExists(UUID scheduleItemId);

  /**
   * Stores an imported execution of a shift.
   *
   * @param aqueductId the demo aqueduct
   * @param row the execution
   * @param importBatchId the import
   * @param recordedBy who imported it
   */
  void insertShiftExecution(
      UUID aqueductId, ShiftExecutionRow row, UUID importBatchId, UUID recordedBy);

  /**
   * Stores an imported report of damage with source {@code IMPORT}.
   *
   * @param aqueductId the demo aqueduct
   * @param row the report
   * @param trackingCodeHash SHA-256 of its tracking code
   * @param importBatchId the import
   * @param importedBy who imported it
   */
  void insertIncident(
      UUID aqueductId,
      IncidentRow row,
      String trackingCodeHash,
      UUID importBatchId,
      UUID importedBy);
}
