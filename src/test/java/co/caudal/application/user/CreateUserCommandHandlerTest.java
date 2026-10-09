package co.caudal.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.auth.SessionScope;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.user.InvalidUsernameException;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.domain.user.ResetCode;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateUserCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final UUID NEW_USER = UUID.randomUUID();
  private static final UUID MEMBERSHIP = UUID.randomUUID();

  private final UserAccountPort accounts = mock(UserAccountPort.class);
  private final MembershipAdminPort memberships = mock(MembershipAdminPort.class);
  private final PasswordResetGrantPort grants = mock(PasswordResetGrantPort.class);
  private final PasswordHasherPort passwords = mock(PasswordHasherPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final CreateUserCommandHandler handler =
      new CreateUserCommandHandler(
          accounts,
          memberships,
          grants,
          passwords,
          new ResetGrantSettings(Duration.ofHours(24)),
          unitOfWork,
          clock);

  @SuppressWarnings("unchecked")
  private void givenHappyWorld() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
    when(passwords.hash(any(NormalizedPassword.class))).thenReturn("argon2-hash-of-nobody");
    when(memberships.roleExists("OPERATOR")).thenReturn(true);
    when(accounts.insertIfAbsent(anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.of(NEW_USER));
    when(memberships.insert(any(), any(), anyString(), any(), any(), any())).thenReturn(MEMBERSHIP);
  }

  private CreateUserCommand command(String username, String fullName, UUID aqueduct, String role) {
    return new CreateUserCommand(
        new Caller(ADMIN, AQUEDUCT, "BOARD_ADMIN", "127.0.0.1"),
        username,
        fullName,
        aqueduct,
        role);
  }

  @Test
  void createsTheAccountTheMembershipAndAOneTimeCode() {
    givenHappyWorld();

    CreatedUser created =
        handler.handle(command("  Operador.Uno ", " Persona   de Ejemplo ", AQUEDUCT, "OPERATOR"));

    assertThat(created.id()).isEqualTo(NEW_USER);
    assertThat(created.username()).isEqualTo("operador.uno");
    assertThat(created.fullName()).isEqualTo("Persona de Ejemplo");
    assertThat(created.mustChangePassword()).isTrue();
    assertThat(created.status()).isEqualTo("ACTIVE");
    assertThat(created.membershipId()).isEqualTo(MEMBERSHIP);
    assertThat(created.activationExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    ResetCode shown = ResetCode.parse(created.activationCode()).orElseThrow();
    ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
    verify(grants)
        .save(eq(NEW_USER), storedHash.capture(), eq(ADMIN), eq(NOW.plus(Duration.ofHours(24))));
    assertThat(storedHash.getValue()).isEqualTo(shown.hash()).isNotEqualTo(shown.value());
    verify(accounts)
        .insertIfAbsent("operador.uno", "Persona de Ejemplo", "argon2-hash-of-nobody", ADMIN);
    verify(memberships).insert(NEW_USER, AQUEDUCT, "OPERATOR", NOW, null, ADMIN);
    verify(unitOfWork).executeAs(eq(new SessionScope(ADMIN, AQUEDUCT)), any());
  }

  @Test
  void theStoredPasswordIsTheHashOfRandomBytesNobodyKnows() {
    givenHappyWorld();

    handler.handle(command("operador.uno", "Persona de Ejemplo", AQUEDUCT, "OPERATOR"));
    handler.handle(command("operador.dos", "Persona de Ejemplo", AQUEDUCT, "OPERATOR"));

    ArgumentCaptor<NormalizedPassword> hashed = ArgumentCaptor.forClass(NormalizedPassword.class);
    verify(passwords, org.mockito.Mockito.times(2)).hash(hashed.capture());
    assertThat(hashed.getAllValues().get(0).value())
        .isNotEqualTo(hashed.getAllValues().get(1).value());
    assertThat(hashed.getAllValues().get(0).length()).isEqualTo(43);
  }

  @Test
  void anAqueductOtherThanTheSessionsIsForbiddenAndNothingIsWritten() {
    givenHappyWorld();

    assertThatThrownBy(
            () ->
                handler.handle(
                    command("operador.uno", "Persona de Ejemplo", UUID.randomUUID(), "OPERATOR")))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

    verifyNoInteractions(accounts, grants);
    verify(unitOfWork, never()).executeAs(any(), any());
  }

  @Test
  void aTakenUsernameIsAConflictAndNoCodeIsIssued() {
    givenHappyWorld();
    when(accounts.insertIfAbsent(anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                handler.handle(command("operador.uno", "Persona de Ejemplo", AQUEDUCT, "OPERATOR")))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));

    verifyNoInteractions(grants);
    verify(memberships, never()).insert(any(), any(), anyString(), any(), any(), any());
  }

  @Test
  void anUnknownRoleIsAFieldErrorBeforeAnythingIsWritten() {
    givenHappyWorld();
    when(memberships.roleExists("GHOST_ROLE")).thenReturn(false);

    assertThatThrownBy(
            () ->
                handler.handle(
                    command("operador.uno", "Persona de Ejemplo", AQUEDUCT, "GHOST_ROLE")))
        .isInstanceOf(FieldValidationException.class);

    verifyNoInteractions(accounts, grants);
  }

  @Test
  void anInvalidUsernameOrNameIsRejectedBeforeOpeningATransaction() {
    assertThatThrownBy(
            () ->
                handler.handle(command("Not A User!", "Persona de Ejemplo", AQUEDUCT, "OPERATOR")))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> handler.handle(command("operador.uno", "<b>Ana</b>", AQUEDUCT, "OPERATOR")))
        .isInstanceOf(FieldValidationException.class);

    verifyNoInteractions(unitOfWork, accounts, passwords);
    assertThat(handler.commandType()).isEqualTo(CreateUserCommand.class);
  }

  @Test
  void theCreatedUserNeverPrintsTheActivationCode() {
    givenHappyWorld();
    CreatedUser created =
        handler.handle(command("operador.uno", "Persona de Ejemplo", AQUEDUCT, "OPERATOR"));

    assertThat(created.toString()).doesNotContain(created.activationCode());
  }
}
