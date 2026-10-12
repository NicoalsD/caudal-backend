package co.caudal.application.port.out;

import co.caudal.application.audit.AuditLogFilter;
import co.caudal.application.audit.AuditLogView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads the audit log of one aqueduct (audit.audit_log), newest first. */
public interface AuditLogQueryPort {

  /**
   * Lists the entries of an aqueduct that match the filters, after the given position.
   *
   * @param aqueductId the aqueduct; only its entries are read
   * @param filter the optional filters
   * @param before moment of the last entry of the previous page, or null for the first page
   * @param beforeId id of the last entry of the previous page, or null for the first page
   * @param limit maximum number of entries
   * @return the entries, newest first
   */
  List<AuditLogView> list(
      UUID aqueductId, AuditLogFilter filter, Instant before, Long beforeId, int limit);
}
