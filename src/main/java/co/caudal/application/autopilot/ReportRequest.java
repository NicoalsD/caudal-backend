package co.caudal.application.autopilot;

import java.time.Instant;
import java.util.Map;

/**
 * What the assistant needs to write a report.
 *
 * @param aqueductName the name of the aqueduct
 * @param from start of the period
 * @param to end of the period
 * @param aggregates the numbers of the period
 */
public record ReportRequest(
    String aqueductName, Instant from, Instant to, Map<String, Object> aggregates) {}
