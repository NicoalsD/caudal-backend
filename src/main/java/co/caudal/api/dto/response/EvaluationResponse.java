package co.caudal.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Whether the AI helps, for one horizon.
 *
 * @param horizonDays the horizon
 * @param from first day evaluated
 * @param to last day evaluated
 * @param n forecasts evaluated
 * @param maeIa mean absolute error of the AI
 * @param maeSimple mean absolute error of the simple estimate
 * @param wqlIa weighted quantile loss of the AI
 * @param coverage80 share of real levels inside [p10, p90]
 * @param avgIntervalWidth mean width of the range
 * @param skill improvement over the simple estimate
 * @param helps true, false, or null without enough evidence
 * @param verdict the answer in Spanish
 * @param isSimulated true when the data are simulated
 * @param banner the mark of simulated data, or null
 */
@Schema(description = "Evaluación de la IA frente a la estimación simple para un horizonte.")
@SuppressWarnings("checkstyle:ParameterNumber")
public record EvaluationResponse(
    @Schema(example = "1") int horizonDays,
    LocalDate from,
    LocalDate to,
    @Schema(example = "34") int n,
    @Schema(nullable = true) BigDecimal maeIa,
    @Schema(nullable = true) BigDecimal maeSimple,
    @Schema(nullable = true) BigDecimal wqlIa,
    @JsonProperty("coverage_80") @Schema(nullable = true, example = "0.78") BigDecimal coverage80,
    @Schema(nullable = true) BigDecimal avgIntervalWidth,
    @Schema(nullable = true, example = "0.12") BigDecimal skill,
    @Schema(nullable = true) Boolean helps,
    String verdict,
    boolean isSimulated,
    @Schema(nullable = true, example = "Datos simulados") String banner) {}
