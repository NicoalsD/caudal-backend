package co.caudal.application.privacy;

import co.caudal.application.auth.Caller;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.PrivacyNoticePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * The privacy notice (docs/Seguridad.md, section 2.17; Ley 1581 de 2012): reads the version in
 * effect and records that a person accepted it, with the moment and the HMAC of the address.
 */
public class PrivacyNoticeService {

  private final PrivacyNoticePort notices;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param notices the notice and its acceptances
   * @param hasher hides the address
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public PrivacyNoticeService(
      PrivacyNoticePort notices, PrivacyHasherPort hasher, UnitOfWorkPort unitOfWork, Clock clock) {
    this.notices = notices;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads the version in effect.
   *
   * @return the notice
   * @throws DomainException with {@code NOT_FOUND} if there is none
   */
  public PrivacyNotice current() {
    return unitOfWork.execute(
        () ->
            notices
                .current(clock.instant())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }

  /**
   * Accepts the version in effect. Accepting it again returns the first acceptance.
   *
   * @param caller the person
   * @param version the version the person read
   * @return the acceptance
   * @throws DomainException with {@code CONFLICT} if the version is not the one in effect
   */
  public PrivacyAcceptance accept(Caller caller, int version) {
    String ipHmac = caller.ipAddress() == null ? null : hasher.hmac(caller.ipAddress());
    return unitOfWork.executeAs(
        SessionScope.ofUser(caller.userId()),
        () -> {
          Instant now = clock.instant();
          PrivacyNotice current =
              notices.current(now).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (current.version() != version) {
            throw new DomainException(
                ErrorCode.CONFLICT, Map.of("current_version", current.version()));
          }
          return notices.accept(caller.userId(), version, ipHmac, now);
        });
  }
}
