package co.caudal.application.device.auth;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;
import java.util.Map;

/**
 * A device request refused by the signature checks. When the situation has a security event
 * (docs/Protocolo-de-dispositivos.md, section 3.2) the failure names it, and the authenticator
 * records it before it throws, so the event survives the rejection.
 */
public class DeviceAuthFailure extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  private final SecurityEventType eventType;
  private final Severity severity;
  private final String reason;

  /**
   * A refusal that records no security event: a missing or repeated header.
   *
   * @param code the error to answer
   */
  public DeviceAuthFailure(ErrorCode code) {
    this(code, null, null, null);
  }

  /**
   * A refusal with its security event.
   *
   * @param code the error to answer
   * @param eventType the event to record, or null for none
   * @param severity the severity of the event, or null for none
   * @param reason the {@code details.reason} of the event, or null for none
   */
  public DeviceAuthFailure(
      ErrorCode code, SecurityEventType eventType, Severity severity, String reason) {
    this(code, eventType, severity, reason, Map.of());
  }

  /**
   * A refusal with its security event and extra details for the answer.
   *
   * @param code the error to answer
   * @param eventType the event to record, or null for none
   * @param severity the severity of the event, or null for none
   * @param reason the {@code details.reason} of the event, or null for none
   * @param details extra details of the answer, such as {@code retry_after_seconds}
   */
  public DeviceAuthFailure(
      ErrorCode code,
      SecurityEventType eventType,
      Severity severity,
      String reason,
      Map<String, Object> details) {
    super(code, details);
    this.eventType = eventType;
    this.severity = severity;
    this.reason = reason;
  }

  /**
   * The event to record.
   *
   * @return the type, or null if the refusal records no event
   */
  public SecurityEventType eventType() {
    return eventType;
  }

  /**
   * The severity of the event.
   *
   * @return the severity, or null if the refusal records no event
   */
  public Severity severity() {
    return severity;
  }

  /**
   * The reason that goes in the details of the event.
   *
   * @return the reason, or null if the refusal records no event
   */
  public String reason() {
    return reason;
  }
}
