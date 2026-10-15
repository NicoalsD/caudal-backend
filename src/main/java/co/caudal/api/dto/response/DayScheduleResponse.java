package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The published schedule of a day.
 *
 * @param serviceDate the day of service
 * @param proposalId the published proposal
 * @param publishedAt when it was published
 * @param items its shifts, with the same shape as in a proposal
 */
@Schema(description = "Horario publicado de un día con los ids de sus turnos.")
public record DayScheduleResponse(
    LocalDate serviceDate,
    UUID proposalId,
    Instant publishedAt,
    List<ProposalResponse.Item> items) {}
