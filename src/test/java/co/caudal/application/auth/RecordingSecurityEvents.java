package co.caudal.application.auth;

import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.domain.security.SecurityEvent;
import java.util.ArrayList;
import java.util.List;

/** Keeps the security events in memory for use case tests. */
class RecordingSecurityEvents implements SecurityEventPort {

  final List<SecurityEvent> recorded = new ArrayList<>();

  @Override
  public void record(SecurityEvent event) {
    recorded.add(event);
  }
}
