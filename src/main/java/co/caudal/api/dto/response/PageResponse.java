package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of a list with a signed cursor (docs/API.md, section 1.5).
 *
 * @param items the items
 * @param nextCursor cursor of the next page
 * @param <T> type of the items
 */
@Schema(description = "Página de una colección con cursor firmado.")
public record PageResponse<T>(
    @Schema(description = "Elementos de la página.") List<T> items,
    @Schema(description = "Cursor de la página siguiente; nulo si no hay más.", nullable = true)
        String nextCursor) {}
