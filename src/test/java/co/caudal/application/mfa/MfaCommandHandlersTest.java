package co.caudal.application.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.auth.LoginAccount;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.SecretCipherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.mfa.Totp;
import co.caudal.domain.mfa.TotpSecret;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MfaCommandHandlersTest {

  private static final Instant NOW = Instant.parse("2026-10-11T13:00:10Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID FACTOR = UUID.randomUUID();
  private static final Caller ADMIN = new Caller(USER, UUID.randomUUID(), "BOARD_ADMIN", null);
  private static final TotpSecret SECRET = TotpSecret.generate();
  private static final SealedSecret SEALED = new SealedSecret(new byte[] {9}, "k1");

  private final MfaFactorPort factors = mock(MfaFactorPort.class);
  private final SecretCipherPort cipher = mock(SecretCipherPort.class);
  private final UserAccountPort accounts = mock(UserAccountPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final MfaVerifier verifier = new MfaVerifier(factors, cipher);
  private final EnrollMfaCommandHandler enroll =
      new EnrollMfaCommandHandler(
          factors,
          cipher,
          accounts,
          new MfaSettings("CAUDAL", Set.of("BOARD_ADMIN", "PROJECT_TEAM")),
          unitOfWork);
  private final ConfirmMfaCommandHandler confirm =
      new ConfirmMfaCommandHandler(factors, verifier, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
    when(accounts.findById(USER))
        .thenReturn(
            Optional.of(
                new LoginAccount(USER, "junta.admin", "Admin", "h", "ACTIVE", null, 0, 0, false)));
    when(cipher.encrypt(any(), eq(USER.toString()))).thenReturn(SEALED);
    when(cipher.decrypt(SEALED, USER.toString())).thenReturn(SECRET.bytes());
  }

  private MfaFactor factor(Instant confirmedAt, Long lastUsedStep) {
    return new MfaFactor(FACTOR, USER, SEALED, confirmedAt, lastUsedStep);
  }

  private static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void enrollingReplacesAPendingFactorAndShowsTheSecretOnce() {
    when(factors.find(USER)).thenReturn(Optional.of(factor(null, null)));

    MfaEnrollment enrollment = enroll.handle(new EnrollMfaCommand(ADMIN));

    assertThat(enrollment.otpauthUri()).contains("junta.admin").contains(enrollment.secret());
    assertThat(enrollment.toString()).doesNotContain(enrollment.secret());
    verify(factors).deleteUnconfirmed(USER);
    verify(factors).insert(USER, SEALED);
    assertThat(enroll.commandType()).isEqualTo(EnrollMfaCommand.class);
  }

  @Test
  void enrollingIsForbiddenForOtherRolesAndAConflictWhenActive() {
    assertThatThrownBy(
            () ->
                enroll.handle(
                    new EnrollMfaCommand(new Caller(USER, UUID.randomUUID(), "OPERATOR", null))))
        .extracting(MfaCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.FORBIDDEN);
    when(factors.find(USER)).thenReturn(Optional.of(factor(NOW, 1L)));
    assertThatThrownBy(() -> enroll.handle(new EnrollMfaCommand(ADMIN)))
        .extracting(MfaCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.CONFLICT);
    verify(factors, never()).insert(any(), any());
  }

  @Test
  void confirmingWithTheCurrentCodeTurnsTheFactorOn() {
    when(factors.find(USER)).thenReturn(Optional.of(factor(null, null)));
    long step = Totp.stepAt(NOW);

    assertThat(confirm.handle(new ConfirmMfaCommand(ADMIN, Totp.codeAt(SECRET, step))))
        .isEqualTo(Done.DONE);

    verify(factors).confirm(FACTOR, NOW, step);
    assertThat(new ConfirmMfaCommand(ADMIN, "123456").toString()).doesNotContain("123456");
    assertThat(confirm.commandType()).isEqualTo(ConfirmMfaCommand.class);
  }

  @Test
  void confirmingFailsWithAWrongCodeWithoutAFactorOrWhenActive() {
    when(factors.find(USER)).thenReturn(Optional.of(factor(null, null)));
    assertThatThrownBy(() -> confirm.handle(new ConfirmMfaCommand(ADMIN, "000000")))
        .isInstanceOf(FieldValidationException.class);

    when(factors.find(USER)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> confirm.handle(new ConfirmMfaCommand(ADMIN, "000000")))
        .extracting(MfaCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.NOT_FOUND);

    when(factors.find(USER)).thenReturn(Optional.of(factor(NOW, 1L)));
    assertThatThrownBy(() -> confirm.handle(new ConfirmMfaCommand(ADMIN, "000000")))
        .extracting(MfaCommandHandlersTest::codeOf)
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  void theVerifierConsumesACodeOnlyOnceEvenUnderARace() {
    long step = Totp.stepAt(NOW);
    String code = Totp.codeAt(SECRET, step);
    when(factors.markUsed(FACTOR, step)).thenReturn(true, false);

    assertThat(verifier.check(factor(NOW, null), code, NOW)).hasValue(step);
    assertThat(verifier.check(factor(NOW, null), code, NOW)).isEmpty();
    assertThat(verifier.check(factor(NOW, step), code, NOW)).isEmpty();
  }
}
