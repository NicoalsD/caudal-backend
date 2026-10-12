package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A publication of a schedule.
 *
 * @param id the publication
 * @param proposalId the proposal published
 * @param publishedAt when
 * @param whatsappText the message to share
 * @param posterSha256 hash of the poster
 */
@Schema(description = "Publicación del horario, sin datos personales.")
public record PublicationResponse(
    UUID id,
    UUID proposalId,
    Instant publishedAt,
    String whatsappText,
    @Schema(nullable = true) String posterSha256) {}
