package co.caudal.application.facade;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.EvaluationPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.evaluation.ForecastEvaluation;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * One call that answers whether the AI helps the aqueduct (docs/Modulo-IA.md, section 9): it hides
 * the pairing of forecasts with real levels, the metric strategies, the verdict rules and the mark
 * of simulated data.
 *
 * @pattern P10 Facade
 */
public class ForecastEvaluationFacade {

  static final String NO_EVIDENCE = "Todavía no hay evidencia suficiente.";
  static final String HELPS = "La IA ayuda: se equivoca menos que la estimación simple.";
  static final String DOES_NOT_HELP =
      "La IA no ayuda todavía: la Junta sigue con la estimación simple.";
  private static final String TO = "to";

  private final AqueductPort aqueducts;
  private final EvaluationPort evaluations;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the facade.
   *
   * @param aqueducts time zone and demo mark
   * @param evaluations comparable days
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ForecastEvaluationFacade(
      AqueductPort aqueducts, EvaluationPort evaluations, UnitOfWorkPort unitOfWork, Clock clock) {
    this.aqueducts = aqueducts;
    this.evaluations = evaluations;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Evaluates one horizon.
   *
   * @param caller who asks
   * @param horizonDays 1 to 3
   * @param from first day, or null for the default window
   * @param to last day, or null for today
   * @return the evaluation
   */
  public EvaluationView evaluate(Caller caller, int horizonDays, LocalDate from, LocalDate to) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          AqueductRef aqueduct =
              aqueducts
                  .findById(caller.aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          ZoneId zone = ZoneId.of(aqueduct.timezone());
          LocalDate last = to != null ? to : LocalDate.ofInstant(clock.instant(), zone);
          LocalDate first =
              from != null ? from : last.minusDays(FieldLimits.EVALUATION_DEFAULT_DAYS);
          if (last.isBefore(first)) {
            throw new FieldValidationException(TO, "FIELD_VALUE_NOT_ALLOWED");
          }
          if (ChronoUnit.DAYS.between(first, last) >= FieldLimits.EVALUATION_MAX_DAYS) {
            throw new FieldValidationException(TO, "FIELD_OUT_OF_RANGE");
          }
          ForecastEvaluation.Report report =
              ForecastEvaluation.evaluate(
                  horizonDays, evaluations.samples(horizonDays, first, last, zone));
          Map<String, java.math.BigDecimal> m = report.metrics();
          Boolean helps = report.helps().orElse(null);
          return new EvaluationView(
              horizonDays,
              first,
              last,
              report.n(),
              m.get("mae_ia"),
              m.get("mae_simple"),
              m.get("wql_ia"),
              m.get("coverage_80"),
              m.get("avg_interval_width"),
              m.get("skill"),
              helps,
              helps == null ? NO_EVIDENCE : helps ? HELPS : DOES_NOT_HELP,
              aqueduct.demo());
        });
  }
}
