package co.caudal.application.auth;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RevocationReason;
import java.time.Clock;
import java.util.Optional;

/**
 * Closes one session: revokes the whole family of the refresh token in the cookie with the reason
 * {@code LOGOUT} (docs/API.md, section 2.3).
 *
 * <p>A token that belongs to another account is ignored, so a caller cannot close somebody else's
 * session by guessing; a missing or unknown token is not an error either, because the goal (no
 * usable session) is already met and the answer does not reveal which tokens exist.
 *
 * @pattern P13 Command
 */
public class LogoutCommandHandler implements CommandHandler<LogoutCommand, Done> {

  private final RefreshTokenPort tokens;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param tokens storage of the refresh tokens
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public LogoutCommandHandler(RefreshTokenPort tokens, UnitOfWorkPort unitOfWork, Clock clock) {
    this.tokens = tokens;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public Done handle(LogoutCommand command) {
    String secret = command.refreshSecret();
    if (!OpaqueToken.isPlausible(secret)) {
      return Done.DONE;
    }
    unitOfWork.execute(
        () -> {
          Optional<RefreshToken> found = tokens.findByHash(OpaqueToken.hashOf(secret));
          found
              .filter(token -> token.userId().equals(command.caller().userId()))
              .ifPresent(
                  token ->
                      tokens.revokeFamily(
                          token.familyId(), RevocationReason.LOGOUT, clock.instant()));
          return Done.DONE;
        });
    return Done.DONE;
  }

  @Override
  public Class<LogoutCommand> commandType() {
    return LogoutCommand.class;
  }
}
