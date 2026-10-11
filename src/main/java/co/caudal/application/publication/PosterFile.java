package co.caudal.application.publication;

/**
 * A rendered poster and the hash kept when it was published.
 *
 * @param pdf the PDF
 * @param sha256 the hash stored in the publication
 */
public record PosterFile(byte[] pdf, String sha256) {}
