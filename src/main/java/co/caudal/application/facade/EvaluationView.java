package co.caudal.application.facade;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Whether the AI helps the aqueduct, for one horizon (docs/API.md, section 14.1).
 *
 * @param horizonDays the horizon
 * @param from first day evaluated
 * @param to last day evaluated
 * @param n forecasts evaluated
 * @param maeIa mean absolute error of the AI, or null
 * @param maeSimple mean absolute error of the simple estimate, or null
 * @param wqlIa weighted quantile loss of the AI, or null
 * @param coverage80 share of real levels inside the range, or null
 * @param avgIntervalWidth mean width of the range, or null
 * @param skill improvement over the simple estimate, or null when it cannot be computed
 * @param helps true, false, or null without enough evidence
 * @param verdict the answer in Spanish
 * @param simulated true when the data of the aqueduct are simulated
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record EvaluationView(
    int horizonDays,
    LocalDate from,
    LocalDate to,
    int n,
    BigDecimal maeIa,
    BigDecimal maeSimple,
    BigDecimal wqlIa,
    BigDecimal coverage80,
    BigDecimal avgIntervalWidth,
    BigDecimal skill,
    Boolean helps,
    String verdict,
    boolean simulated) {}
