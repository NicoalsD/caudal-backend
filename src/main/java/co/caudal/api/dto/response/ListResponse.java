package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A collection that is not paginated.
 *
 * @param items the elements
 * @param <T> the type of the elements
 */
@Schema(description = "Colección de elementos.")
public record ListResponse<T>(@Schema(description = "Elementos de la lista.") List<T> items) {}
