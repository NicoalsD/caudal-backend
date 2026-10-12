package co.caudal.application.summary;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A summary an entity may read: aggregated counts for a period, through one authorization. It has
 * no reason, no author and no text of any record.
 *
 * @param summaryShareId the authorization that allows it
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day
 * @param periodTo last day
 * @param content the counts per section
 */
public record SummaryView(
    UUID summaryShareId,
    String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    SummarySections content) {}
