package co.caudal.application.publication;

import java.time.Instant;
import java.util.UUID;

/**
 * A publication of a schedule.
 *
 * @param id the publication
 * @param proposalId the proposal published
 * @param publishedAt when
 * @param whatsappText the message to share, without personal data
 * @param posterSha256 hash of the poster, or null
 */
public record PublicationView(
    UUID id, UUID proposalId, Instant publishedAt, String whatsappText, String posterSha256) {}
