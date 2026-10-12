package co.caudal.application.minutes;

/**
 * The PDF of some minutes.
 *
 * @param pdf the bytes
 * @param sha256 the hash kept when they were finalized, or null for a draft
 * @param fileName the name to save it with
 */
public record MinutesPdf(byte[] pdf, String sha256, String fileName) {}
