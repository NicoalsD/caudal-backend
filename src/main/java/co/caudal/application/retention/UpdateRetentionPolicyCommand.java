package co.caudal.application.retention;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.Map;

/**
 * Changes the retention period of one class of data (docs/API.md, section 18.3). The change is
 * written to the audit log as {@code RETENTION_POLICY_CHANGED}, with the new period.
 *
 * @param caller the Board administrator
 * @param dataClass the class of data
 * @param retentionDays the new period, in days
 */
public record UpdateRetentionPolicyCommand(Caller caller, String dataClass, int retentionDays)
    implements AuditableCommand<RetentionPolicyView> {

  /** Action of the audit log for a change of retention. */
  static final String ACTION = "RETENTION_POLICY_CHANGED";

  @Override
  public String auditEntityType() {
    return "retention_policy";
  }

  @Override
  public String auditEntityId(RetentionPolicyView result) {
    return dataClass;
  }

  @Override
  public String auditAction() {
    return ACTION;
  }

  @Override
  public Map<String, Object> auditAfterState(RetentionPolicyView result) {
    return Map.of("data_class", dataClass, "retention_days", retentionDays);
  }
}
