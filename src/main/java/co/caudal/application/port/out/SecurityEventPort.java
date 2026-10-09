package co.caudal.application.port.out;

import co.caudal.domain.security.SecurityEvent;

/** Records security events in {@code iam.security_events}. */
public interface SecurityEventPort {

  /**
   * Records an event. It joins the current transaction, so it is saved together with the change
   * that caused it.
   *
   * @param event the event to record
   */
  void record(SecurityEvent event);
}
