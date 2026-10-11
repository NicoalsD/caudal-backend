package co.caudal.application.port.out;

import co.caudal.application.command.AuditEntry;

/**
 * Appends to the audit log ({@code audit.audit_log}). The database links each row to the previous
 * one of its aqueduct with a SHA-256 chain, so a changed or deleted row is detected. It must run in
 * a transaction scoped to the aqueduct of the entry.
 */
public interface AuditLogPort {

  /**
   * Appends an entry.
   *
   * @param entry the entry
   */
  void append(AuditEntry entry);
}
