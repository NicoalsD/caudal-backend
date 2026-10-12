package co.caudal.application.command;

import co.caudal.application.auth.Caller;
import java.util.Map;

/**
 * A command whose success is written to the audit log ({@code audit.audit_log}) by {@link
 * AuditingCommandHandler}: who did it, on which entity and when, in the hash chain of the aqueduct.
 *
 * @param <R> type of the result
 */
public interface AuditableCommand<R> extends Command<R> {

  /**
   * Who sends the command.
   *
   * @return the caller
   */
  Caller caller();

  /**
   * Kind of the entity the command changes, for example {@code user}.
   *
   * @return the entity type
   */
  String auditEntityType();

  /**
   * Id of the entity the command changed, read from the result when the command creates it.
   *
   * @param result the result of the command
   * @return the entity id
   */
  String auditEntityId(R result);

  /**
   * Action written to the log, for example {@code RETENTION_POLICY_CHANGED}. By default the
   * decorator derives it from the name of the command.
   *
   * @return the action, or null to derive it from the name of the command
   */
  default String auditAction() {
    return null;
  }

  /**
   * State after the change, already redacted, written with the entry. By default there is none.
   *
   * @param result the result of the command
   * @return the state, or null
   */
  default Map<String, Object> auditAfterState(R result) {
    return null;
  }
}
