package co.caudal.application.reading;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.RuleSetView;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * Validates and stores one reading sent by the app (docs/API.md, section 7.1) with the {@link
 * ReadingRecorder}.
 *
 * @pattern P13 Command
 */
public class CreateReadingCommandHandler
    implements CommandHandler<CreateReadingCommand, RecordedReading> {

  private final RuleSetPort ruleSets;
  private final ReadingRecorder recorder;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param ruleSets rules
   * @param recorder judges and stores readings
   * @param unitOfWork transaction boundary
   */
  public CreateReadingCommandHandler(
      RuleSetPort ruleSets, ReadingRecorder recorder, UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.recorder = recorder;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public RecordedReading handle(CreateReadingCommand command) {
    ReadingInput input =
        new ReadingInput(
            command.id(),
            command.tankId(),
            command.gaugeValue(),
            command.waterAppearanceCode(),
            command.damageNoticed(),
            ReadingRecorder.normalizeNote(command.note()),
            command.observedAt());
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          RuleSetView rules =
              ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return recorder.store(
              command.caller(),
              recorder.judge(command.caller(), input, rules, false),
              rules,
              ReadingRecorder.SOURCE_MANUAL,
              null);
        });
  }

  @Override
  public Class<CreateReadingCommand> commandType() {
    return CreateReadingCommand.class;
  }
}
