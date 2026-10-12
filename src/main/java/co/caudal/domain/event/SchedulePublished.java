package co.caudal.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Board published the schedule of a day.
 *
 * @param aqueductId the aqueduct
 * @param proposalId the proposal published
 * @param publicationId the publication
 * @param serviceDate the day of service
 * @param publishedBy who published it
 * @param occurredAt when
 */
public record SchedulePublished(
    UUID aqueductId,
    UUID proposalId,
    UUID publicationId,
    LocalDate serviceDate,
    UUID publishedBy,
    Instant occurredAt)
    implements DomainEvent {}
