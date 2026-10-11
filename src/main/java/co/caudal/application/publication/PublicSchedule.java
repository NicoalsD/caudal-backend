package co.caudal.application.publication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The schedule of a day of an aqueduct as stored. The fields marked internal never reach the
 * public: {@code PublicScheduleProxy} removes them.
 *
 * @param aqueductName the aqueduct
 * @param demo true if its data are simulated
 * @param publicPageEnabled true if the aqueduct shows its page
 * @param serviceDate the day of service
 * @param proposalStatus status of the proposal (internal)
 * @param publicationId the publication, or null if not published
 * @param publishedAt when, or null
 * @param publishedBy who published it (internal, personal data)
 * @param whatsappText the message, or null
 * @param posterSha256 hash of the poster, or null
 * @param lines the shifts
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record PublicSchedule(
    String aqueductName,
    boolean demo,
    boolean publicPageEnabled,
    LocalDate serviceDate,
    String proposalStatus,
    UUID publicationId,
    Instant publishedAt,
    UUID publishedBy,
    String whatsappText,
    String posterSha256,
    List<ScheduleLine> lines) {

  /** Copies the shifts. */
  public PublicSchedule {
    lines = List.copyOf(lines);
  }
}
