package co.caudal.application.port.out;

import co.caudal.application.publication.PublicationView;
import java.util.UUID;

/** Publications of schedules (append-only). */
public interface PublicationPort {

  /**
   * Stores a publication.
   *
   * @param aqueductId the aqueduct
   * @param proposalId the proposal
   * @param publishedBy who published it
   * @param whatsappText the message
   * @param posterSha256 hash of the poster, or null
   * @return the publication
   */
  PublicationView insert(
      UUID aqueductId, UUID proposalId, UUID publishedBy, String whatsappText, String posterSha256);
}
