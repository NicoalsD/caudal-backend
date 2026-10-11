package co.caudal.application.command;

import co.caudal.application.auth.Caller;

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
}
