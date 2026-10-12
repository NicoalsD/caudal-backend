package co.caudal.application.minutes;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.hash.Sha256;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

/**
 * Closes a draft as the final minutes (docs/Reglas-de-negocio.md, section 16.2): it keeps who and
 * when, the head of the audit chain so anyone can check later that the log was not changed, and the
 * SHA-256 of the final PDF, which is rendered again byte for byte on every download. Only a draft
 * moves; a final document is immutable, also in the database.
 *
 * @pattern P13 Command
 */
public class FinalizeMinutesCommandHandler
    implements CommandHandler<FinalizeMinutesCommand, MinutesView> {

  static final String DRAFT = "DRAFT";
  static final String FINAL = "FINAL";

  private final MinutesPort minutes;
  private final AqueductPort aqueducts;
  private final DocumentRenderingPort documents;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param minutes minutes
   * @param aqueducts name, demo mark and time zone
   * @param documents renders the final PDF
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public FinalizeMinutesCommandHandler(
      MinutesPort minutes,
      AqueductPort aqueducts,
      DocumentRenderingPort documents,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.minutes = minutes;
    this.aqueducts = aqueducts;
    this.documents = documents;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public MinutesView handle(FinalizeMinutesCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          MinutesView draft =
              minutes
                  .find(command.minutesId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (!DRAFT.equals(draft.status())) {
            throw new DomainException(
                ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", draft.status(), "to", FINAL));
          }
          AqueductRef aqueduct =
              aqueducts
                  .findById(command.caller().aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          String head = minutes.auditHeadHash().orElse(null);
          byte[] pdf =
              documents.minutes(
                  MinutesDocuments.of(
                      draft,
                      aqueduct.name(),
                      aqueduct.demo(),
                      ZoneId.of(aqueduct.timezone()),
                      false,
                      head));
          if (!minutes.finalizeDraft(
              draft.id(), command.caller().userId(), clock.instant(), Sha256.hex(pdf), head)) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          return minutes.find(draft.id()).orElseThrow();
        });
  }

  @Override
  public Class<FinalizeMinutesCommand> commandType() {
    return FinalizeMinutesCommand.class;
  }
}
