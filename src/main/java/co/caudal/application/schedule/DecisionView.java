package co.caudal.application.schedule;

import java.time.Instant;
import java.util.UUID;

/**
 * A decision of the Board on a proposal.
 *
 * @param decision {@code APPROVED}, {@code APPROVED_WITH_CHANGES} or {@code REJECTED}
 * @param reason why, or null for a plain approval
 * @param decidedBy who decided
 * @param decidedAt when
 */
public record DecisionView(String decision, String reason, UUID decidedBy, Instant decidedAt) {}
