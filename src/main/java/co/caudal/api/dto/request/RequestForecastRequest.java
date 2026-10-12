package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A forecast asked now.
 *
 * @param horizonDays days to forecast, 1 to 3; the rules decide when missing
 */
@Schema(description = "Pronóstico pedido ahora.")
public record RequestForecastRequest(
    @Schema(
            description = "Días a pronosticar, de 1 a 3; por defecto, forecast_horizon_days.",
            example = "1",
            nullable = true)
        @Min(FieldLimits.FORECAST_HORIZON_DAYS_MIN)
        @Max(FieldLimits.FORECAST_HORIZON_DAYS_MAX)
        Integer horizonDays) {}
