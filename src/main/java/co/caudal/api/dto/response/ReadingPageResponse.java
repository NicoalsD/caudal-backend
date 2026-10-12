package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of readings.
 *
 * @param items readings, newest first
 * @param nextCursor cursor of the next page
 */
@Schema(description = "Página de lecturas con cursor firmado.")
public record ReadingPageResponse(
    @Schema(description = "Lecturas, de la más reciente a la más antigua.")
        List<ReadingResponse> items,
    @Schema(description = "Cursor de la página siguiente; nulo si no hay más.", nullable = true)
        String nextCursor) {}
