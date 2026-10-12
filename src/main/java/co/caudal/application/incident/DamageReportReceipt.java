package co.caudal.application.incident;

/**
 * The answer to a report.
 *
 * @param trackingCode the code to follow it, shown only once
 * @param status the status of the report
 * @param message the text to show to the person
 */
public record DamageReportReceipt(String trackingCode, String status, String message) {}
