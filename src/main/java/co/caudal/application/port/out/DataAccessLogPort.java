package co.caudal.application.port.out;

import java.util.UUID;

/** Who read sensitive documents (audit.data_access_log). */
public interface DataAccessLogPort {

  /**
   * Writes one access.
   *
   * @param aqueductId the aqueduct
   * @param userId who read it
   * @param resource what kind of document, such as MINUTES_PDF
   * @param resourceId its id
   * @param ipHmac HMAC of the address, or null
   */
  void record(UUID aqueductId, UUID userId, String resource, String resourceId, String ipHmac);
}
