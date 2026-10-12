package co.caudal.application.port.out;

import co.caudal.application.retention.RetentionPolicyView;
import java.util.Optional;
import java.util.UUID;

/** Reads and changes the retention periods (audit.retention_policies). */
public interface RetentionPolicyPort {

  /**
   * Sets the period of one class of data.
   *
   * @param dataClass the class of data
   * @param retentionDays the new period, in days
   * @param updatedBy the account that makes the change
   * @return the period as stored, or empty if the class has no policy
   */
  Optional<RetentionPolicyView> update(String dataClass, int retentionDays, UUID updatedBy);
}
