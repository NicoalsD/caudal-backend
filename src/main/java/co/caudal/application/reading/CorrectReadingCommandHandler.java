package co.caudal.application.reading;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.reading.ReadingRules;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * Corrects a reading (docs/Reglas-de-negocio.md, section 9): a new row in {@code
 * ops.reading_corrections}, never a change of the original; the effective value comes from the view
 * {@code ops.effective_readings}, where the latest correction wins. The corrected value must be on
 * the gauge of the tank and the reason is required. The Board corrects any reading of its aqueduct;
 * the plumber only the ones they recorded.
 *
 * @pattern P13 Command
 */
public class CorrectReadingCommandHandler
    implements CommandHandler<CorrectReadingCommand, CorrectionResult> {

  /** Role that may only correct its own readings. */
  static final String OWN_READINGS_ONLY_ROLE = "OPERATOR";

  private final ReadingPort readings;
  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param readings readings
   * @param network tanks, for the gauge
   * @param unitOfWork transaction boundary
   */
  public CorrectReadingCommandHandler(
      ReadingPort readings, NetworkPort network, UnitOfWorkPort unitOfWork) {
    this.readings = readings;
    this.network = network;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public CorrectionResult handle(CorrectReadingCommand command) {
    ChangeReason reason = ChangeReason.require(command.reason());
    return unitOfWork.executeAs(command.caller().scope(), () -> correct(command, reason));
  }

  private CorrectionResult correct(CorrectReadingCommand command, ChangeReason reason) {
    ReadingView reading =
        readings
            .findById(command.readingId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (OWN_READINGS_ONLY_ROLE.equals(command.caller().role())) {
      UUID author = readings.recordedBy(reading.id()).orElse(null);
      if (!command.caller().userId().equals(author)) {
        throw new DomainException(ErrorCode.FORBIDDEN);
      }
    }
    TankView tank =
        network.tank(reading.tankId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (!ReadingRules.isWithinGauge(
        command.correctedGaugeValue(), tank.gaugeMin(), tank.gaugeMax())) {
      throw new DomainException(
          ErrorCode.GAUGE_OUT_OF_RANGE,
          Map.of(
              "gauge_min", tank.gaugeMin().toPlainString(),
              "gauge_max", tank.gaugeMax().toPlainString(),
              "value", command.correctedGaugeValue().toPlainString()));
    }
    CorrectionView correction =
        readings.insertCorrection(
            command.caller().aqueductId(),
            reading.id(),
            command.correctedGaugeValue(),
            reason.value(),
            command.caller().userId());
    return new CorrectionResult(
        correction, readings.effectiveValue(reading.id()).orElse(correction.correctedGaugeValue()));
  }

  @Override
  public Class<CorrectReadingCommand> commandType() {
    return CorrectReadingCommand.class;
  }
}
