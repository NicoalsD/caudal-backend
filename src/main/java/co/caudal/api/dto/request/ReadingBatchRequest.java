package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The offline queue of the app.
 *
 * @param syncBatchId the id the app chose for this batch
 * @param readings the readings, 1 to 100
 */
@Schema(description = "Lote de lecturas de la cola offline.")
public record ReadingBatchRequest(
    @Schema(description = "UUID del lote, generado por la app; hace el lote idempotente.") @NotNull
        UUID syncBatchId,
    @Schema(description = "Lecturas del lote, de 1 a 100, con los campos de POST /readings.")
        @NotNull
        @Size(min = 1, max = FieldLimits.LIST_MAX_ITEMS)
        List<@Valid @NotNull CreateReadingRequest> readings) {}
