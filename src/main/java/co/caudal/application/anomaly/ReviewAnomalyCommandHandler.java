package co.caudal.application.anomaly;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.AnomalyPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.anomaly.AnomalyState;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;

/**
 * Resolves an anomaly with the rules of {@link AnomalyState}: only an open one changes, and a
 * dismissal needs its reason. A confirmed anomaly may point to the incident it led to.
 *
 * @pattern P13 Command
 */
public class ReviewAnomalyCommandHandler
    implements CommandHandler<ReviewAnomalyCommand, AnomalyView> {

  private final AnomalyPort anomalies;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param anomalies anomalies
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ReviewAnomalyCommandHandler(
      AnomalyPort anomalies, UnitOfWorkPort unitOfWork, Clock clock) {
    this.anomalies = anomalies;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public AnomalyView handle(ReviewAnomalyCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          AnomalyView current =
              anomalies
                  .find(command.anomalyId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          AnomalyState next =
              AnomalyState.of(current.status()).resolve(command.status(), command.resolutionNote());
          if (command.relatedIncidentId() != null
              && !anomalies.incidentExists(command.relatedIncidentId())) {
            throw new FieldValidationException("related_incident_id", "FIELD_VALUE_NOT_ALLOWED");
          }
          String note =
              command.resolutionNote() == null || command.resolutionNote().isBlank()
                  ? null
                  : command.resolutionNote().strip();
          anomalies.resolve(
              current.id(),
              next.code(),
              command.caller().userId(),
              clock.instant(),
              note,
              command.relatedIncidentId());
          return anomalies.find(current.id()).orElseThrow();
        });
  }

  @Override
  public Class<ReviewAnomalyCommand> commandType() {
    return ReviewAnomalyCommand.class;
  }
}
