package co.caudal.application.institution;

import java.util.List;

/** Public list of institutions, used by the login screen before anyone signs in. */
public interface PublicInstitutionDirectory {

  /**
   * Lists the active institutions as {@code slug} and {@code name}, ordered by name.
   *
   * @param ipAddress address of the caller, used for the rate limit (may be null)
   * @return the institutions
   * @throws co.caudal.shared.error.DomainException with {@code RATE_LIMITED} over the limit
   */
  List<PublicInstitution> list(String ipAddress);
}
