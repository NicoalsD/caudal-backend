package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An option of a list.
 *
 * @param code code in English
 * @param labelEs label in Spanish
 * @param sortOrder order
 */
@Schema(description = "Opción de una lista con su etiqueta en español.")
public record CatalogItemResponse(
    @Schema(description = "Código en inglés.", example = "MUDDY") String code,
    @Schema(description = "Etiqueta visible.", example = "Con barro") String labelEs,
    @Schema(description = "Orden.", example = "2") int sortOrder) {}
