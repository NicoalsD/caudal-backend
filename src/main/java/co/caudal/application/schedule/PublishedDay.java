package co.caudal.application.schedule;

import java.time.Instant;

/**
 * The published schedule of a day (docs/API.md, section 11.4).
 *
 * @param proposal the proposal that was published, with its shifts and reasons
 * @param publishedAt when it was published
 */
public record PublishedDay(ProposalDetail proposal, Instant publishedAt) {}
