package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A summary shared with an entity.
 *
 * @param summaryShareId the authorization that allows it
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day
 * @param periodTo last day
 * @param content the counts per section
 */
@Schema(description = "Resumen agregado de un periodo, autorizado por la Junta.")
public record SummaryResponse(
    UUID summaryShareId,
    @Schema(example = "MONTHLY_SUMMARY") String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    SummarySectionsResponse content) {}
