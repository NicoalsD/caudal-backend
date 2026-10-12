package co.caudal.application.port.out;

import co.caudal.application.audit.SecurityEventFilter;
import co.caudal.application.audit.SecurityEventView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads the security events of one aqueduct (iam.security_events), newest first. */
public interface SecurityEventQueryPort {

  /**
   * Lists the events of an aqueduct that match the filters, after the given position. Events
   * without an aqueduct are never returned.
   *
   * @param aqueductId the aqueduct; only its events are read
   * @param filter the optional filters
   * @param before moment of the last event of the previous page, or null for the first page
   * @param beforeId id of the last event of the previous page, or null for the first page
   * @param limit maximum number of events
   * @return the events, newest first
   */
  List<SecurityEventView> list(
      UUID aqueductId, SecurityEventFilter filter, Instant before, Long beforeId, int limit);
}
