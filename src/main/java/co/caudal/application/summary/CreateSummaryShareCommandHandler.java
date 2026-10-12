package co.caudal.application.summary;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import co.caudal.shared.text.SafeText;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores an authorization for an entity. The grantee must hold a current {@code SUPPORT_ENTITY}
 * membership in the aqueduct of the Board member, the period must not be inverted and the expiry
 * must be in the future. A short reason answers {@code REASON_REQUIRED}.
 *
 * @pattern P13 Command
 */
public class CreateSummaryShareCommandHandler
    implements CommandHandler<CreateSummaryShareCommand, SummaryShareView> {

  static final String SUPPORT_ENTITY = "SUPPORT_ENTITY";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private final SummarySharePort shares;
  private final MembershipPort memberships;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param shares authorizations
   * @param memberships memberships of the grantee
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public CreateSummaryShareCommandHandler(
      SummarySharePort shares, MembershipPort memberships, UnitOfWorkPort unitOfWork, Clock clock) {
    this.shares = shares;
    this.memberships = memberships;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public SummaryShareView handle(CreateSummaryShareCommand command) {
    String reason = SafeText.optionalLine("reason", command.reason(), FieldLimits.REASON_MAX);
    if (reason == null || reason.codePointCount(0, reason.length()) < FieldLimits.REASON_MIN) {
      throw new DomainException(ErrorCode.REASON_REQUIRED);
    }
    if (command.periodTo().isBefore(command.periodFrom())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    Instant now = clock.instant();
    if (command.expiresAt() == null || !command.expiresAt().isAfter(now)) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          UUID aqueductId = command.caller().aqueductId();
          if (!isSupportEntityOf(command.granteeUserId(), aqueductId, now)) {
            throw new FieldValidationException("grantee_user_id", NOT_ALLOWED);
          }
          UUID id =
              shares.insert(
                  aqueductId,
                  new NewSummaryShare(
                      command.granteeUserId(),
                      command.scope(),
                      command.periodFrom(),
                      command.periodTo(),
                      reason,
                      command.expiresAt(),
                      command.caller().userId()));
          return shares.find(id).orElseThrow();
        });
  }

  private boolean isSupportEntityOf(UUID userId, UUID aqueductId, Instant now) {
    return memberships.findActive(userId, now).stream()
        .anyMatch(
            membership ->
                aqueductId.equals(membership.aqueductId())
                    && SUPPORT_ENTITY.equals(membership.role()));
  }

  @Override
  public Class<CreateSummaryShareCommand> commandType() {
    return CreateSummaryShareCommand.class;
  }
}
