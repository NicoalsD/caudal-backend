package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Counts of a summary per section of the minutes.
 *
 * @param observed records seen or measured
 * @param estimated records calculated
 * @param inferred records suspected
 * @param confirmed records verified in the field
 */
@Schema(description = "Cantidad de registros por sección del acta. Solo cifras, sin texto.")
public record SummarySectionsResponse(
    @Schema(example = "12") int observed,
    @Schema(example = "3") int estimated,
    @Schema(example = "1") int inferred,
    @Schema(example = "2") int confirmed) {}
