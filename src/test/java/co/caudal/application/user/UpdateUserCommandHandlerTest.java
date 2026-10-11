package co.caudal.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.auth.SessionInvalidator;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.domain.session.RevocationReason;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateUserCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-11T13:00:00Z");
  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();

  private final UserAdminPort users = mock(UserAdminPort.class);
  private final SessionInvalidator sessions = mock(SessionInvalidator.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final UpdateUserCommandHandler handler =
      new UpdateUserCommandHandler(users, sessions, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  @SuppressWarnings("unchecked")
  void runTransactionsInline() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
  }

  private void given(String status, Instant lockedUntil) {
    when(users.findInAqueduct(AQUEDUCT, USER))
        .thenReturn(
            Optional.of(
                new ManagedUser(
                    USER, "operador.uno", "Persona Uno", status, lockedUntil, false, null)));
  }

  private UpdateUserCommand command(UUID target, String fullName, String status) {
    return new UpdateUserCommand(
        new Caller(ADMIN, AQUEDUCT, "BOARD_ADMIN", "127.0.0.1"), target, fullName, status);
  }

  @Test
  void renamesWithoutTouchingTheSessions() {
    given("ACTIVE", null);

    ManagedUser user = handler.handle(command(USER, "  Persona   Nueva ", null));

    assertThat(user.fullName()).isEqualTo("Persona Nueva");
    assertThat(user.status()).isEqualTo("ACTIVE");
    verify(users).saveAdministration(USER, "Persona Nueva", "ACTIVE", null, null, false);
    verify(sessions, never()).invalidate(any(), any());
  }

  @Test
  void lockingEndsEverySession() {
    given("ACTIVE", null);

    ManagedUser user = handler.handle(command(USER, null, "LOCKED"));

    assertThat(user.status()).isEqualTo("LOCKED");
    assertThat(user.lockedUntil()).isNull();
    verify(users).saveAdministration(USER, "Persona Uno", "LOCKED", null, null, false);
    verify(sessions).invalidate(USER, RevocationReason.ADMIN);
  }

  @Test
  void disablingRecordsTheMomentAndEndsEverySession() {
    given("LOCKED", NOW.plusSeconds(600));

    handler.handle(command(USER, null, "DISABLED"));

    verify(users).saveAdministration(USER, "Persona Uno", "DISABLED", null, NOW, false);
    verify(sessions).invalidate(USER, RevocationReason.ADMIN);
  }

  @Test
  void unlockingClearsTheFailedLogins() {
    given("LOCKED", NOW.plusSeconds(600));

    ManagedUser user = handler.handle(command(USER, null, "ACTIVE"));

    assertThat(user.status()).isEqualTo("ACTIVE");
    verify(users).saveAdministration(USER, "Persona Uno", "ACTIVE", null, null, true);
    verify(sessions, never()).invalidate(any(), any());
  }

  @Test
  void keepingTheSameStatusChangesNothingElse() {
    given("DISABLED", null);

    handler.handle(command(USER, null, "DISABLED"));

    verify(users).saveAdministration(eq(USER), any(), eq("DISABLED"), isNull(), any(), eq(false));
    verify(sessions, never()).invalidate(any(), any());
  }

  @Test
  void aDisabledAccountDoesNotComeBack() {
    given("DISABLED", null);

    assertThatThrownBy(() -> handler.handle(command(USER, null, "ACTIVE")))
        .isInstanceOf(DomainException.class)
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    verify(users, never()).saveAdministration(any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void anAdministratorCannotLockThemselves() {
    when(users.findInAqueduct(AQUEDUCT, ADMIN))
        .thenReturn(
            Optional.of(
                new ManagedUser(ADMIN, "junta.admin", "Admin", "ACTIVE", null, false, null)));

    assertThatThrownBy(() -> handler.handle(command(ADMIN, null, "LOCKED")))
        .isInstanceOf(DomainException.class)
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.CONFLICT);
    assertThat(handler.handle(command(ADMIN, "Admin Nuevo", null)).fullName())
        .isEqualTo("Admin Nuevo");
  }

  @Test
  void anAccountOutsideTheAqueductIsNotFound() {
    when(users.findInAqueduct(AQUEDUCT, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(command(USER, null, "LOCKED")))
        .isInstanceOf(DomainException.class)
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(handler.commandType()).isEqualTo(UpdateUserCommand.class);
  }
}
