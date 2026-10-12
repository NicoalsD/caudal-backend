package co.caudal.application.minutes;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.MinutesSourcePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.minutes.Minutes;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Generates the minutes of a period as a draft: it reads every record between the first and the
 * last local day of the aqueduct and classifies them in the four sections.
 *
 * @pattern P13 Command
 */
public class GenerateMinutesCommandHandler
    implements CommandHandler<GenerateMinutesCommand, MinutesView> {

  private static final String PERIOD_TO = "period_to";

  private final AqueductPort aqueducts;
  private final MinutesSourcePort sources;
  private final MinutesPort minutes;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param aqueducts time zone
   * @param sources records of the period
   * @param minutes minutes
   * @param unitOfWork transaction boundary
   */
  public GenerateMinutesCommandHandler(
      AqueductPort aqueducts,
      MinutesSourcePort sources,
      MinutesPort minutes,
      UnitOfWorkPort unitOfWork) {
    this.aqueducts = aqueducts;
    this.sources = sources;
    this.minutes = minutes;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public MinutesView handle(GenerateMinutesCommand command) {
    if (command.periodTo().isBefore(command.periodFrom())) {
      throw new FieldValidationException(PERIOD_TO, "FIELD_VALUE_NOT_ALLOWED");
    }
    if (ChronoUnit.DAYS.between(command.periodFrom(), command.periodTo())
        >= FieldLimits.MINUTES_MAX_DAYS) {
      throw new FieldValidationException(PERIOD_TO, "FIELD_OUT_OF_RANGE");
    }
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          AqueductRef aqueduct =
              aqueducts
                  .findById(command.caller().aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          ZoneId zone = ZoneId.of(aqueduct.timezone());
          Minutes content =
              MinutesAssembler.assemble(
                  command.periodFrom(),
                  command.periodTo(),
                  sources.facts(
                      command.periodFrom().atStartOfDay(zone).toInstant(),
                      command.periodTo().plusDays(1).atStartOfDay(zone).toInstant()));
          UUID id = minutes.insert(aqueduct.id(), content, command.caller().userId());
          return minutes.find(id).orElseThrow();
        });
  }

  @Override
  public Class<GenerateMinutesCommand> commandType() {
    return GenerateMinutesCommand.class;
  }
}
