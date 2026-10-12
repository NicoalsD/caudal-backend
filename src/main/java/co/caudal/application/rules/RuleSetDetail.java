package co.caudal.application.rules;

import java.time.Instant;
import java.util.UUID;

/**
 * A full version of the rules with its history data (docs/API.md, section 6.3).
 *
 * @param rules the parameters and children
 * @param status {@code DRAFT}, {@code ACTIVE}, {@code SUPERSEDED} or {@code DISCARDED}
 * @param basedOnRuleSetId the version it was copied from, or null
 * @param validTo end of its validity, or null
 * @param changeReason why the Board changed the rules, or null in a draft without it
 * @param createdBy who created it
 * @param createdAt when it was created
 * @param activatedBy who activated it, or null
 * @param activatedAt when it was activated, or null
 */
public record RuleSetDetail(
    RuleSetView rules,
    String status,
    UUID basedOnRuleSetId,
    Instant validTo,
    String changeReason,
    UUID createdBy,
    Instant createdAt,
    UUID activatedBy,
    Instant activatedAt) {}
