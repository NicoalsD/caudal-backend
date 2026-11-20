package co.caudal.application.autopilot;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.ReportPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The reports of a period (HU-44): the numbers, and the narrative that the assistant writes from
 * them. The narrative is requested outside any transaction and, when the service fails, comes from
 * a template.
 */
public class ReportUseCases {

  private final ReportPort reports;
  private final AqueductPort aqueducts;
  private final InsightsPort insights;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use cases.
   *
   * @param reports the numbers of a period
   * @param aqueducts the aqueduct, for its name
   * @param insights the assistant
   * @param unitOfWork transaction boundary
   */
  public ReportUseCases(
      ReportPort reports,
      AqueductPort aqueducts,
      InsightsPort insights,
      UnitOfWorkPort unitOfWork) {
    this.reports = reports;
    this.aqueducts = aqueducts;
    this.insights = insights;
    this.unitOfWork = unitOfWork;
  }

  /**
   * The numbers of a period.
   *
   * @param caller who asks
   * @param from start of the period, inclusive
   * @param to end of the period, exclusive
   * @return the aggregates
   * @throws DomainException with {@code VALIDATION_ERROR} if the period is empty, reversed or
   *     longer than the limit
   */
  public ReportView aggregates(Caller caller, Instant from, Instant to) {
    check(from, to);
    Map<String, Object> aggregates =
        unitOfWork.executeAs(caller.scope(), () -> reports.aggregates(from, to));
    return new ReportView(from, to, aggregates);
  }

  /**
   * The narrative of a period.
   *
   * @param caller who asks
   * @param from start of the period, inclusive
   * @param to end of the period, exclusive
   * @return the report written by the assistant or by the rules
   * @throws DomainException with {@code VALIDATION_ERROR} if the period is not valid
   */
  public InsightsReport narrative(Caller caller, Instant from, Instant to) {
    check(from, to);
    ReportRequest request =
        unitOfWork.executeAs(
            caller.scope(),
            () ->
                new ReportRequest(
                    aqueducts.findById(caller.aqueductId()).map(AqueductRef::name).orElse(""),
                    from,
                    to,
                    reports.aggregates(from, to)));
    return insights.report(request);
  }

  private static void check(Instant from, Instant to) {
    if (from == null
        || to == null
        || !to.isAfter(from)
        || Duration.between(from, to).compareTo(Duration.ofDays(FieldLimits.REPORT_PERIOD_DAYS_MAX))
            > 0) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "to"));
    }
  }
}
