package co.caudal.application.autopilot;

import java.time.Instant;
import java.util.Map;

/**
 * The numbers of a period.
 *
 * @param from start of the period
 * @param to end of the period
 * @param aggregates groups of numbers by topic
 */
public record ReportView(Instant from, Instant to, Map<String, Object> aggregates) {}
