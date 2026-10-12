package co.caudal.application.privacy;

import java.time.Instant;

/**
 * A version of the privacy notice.
 *
 * @param version its number
 * @param textEs the text in Spanish
 * @param textSha256 SHA-256 of the text, to prove which one was accepted
 * @param effectiveAt since when it applies
 */
public record PrivacyNotice(int version, String textEs, String textSha256, Instant effectiveAt) {}
