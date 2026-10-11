package co.caudal.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditingCommandHandlerTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final Caller CALLER = new Caller(USER, AQUEDUCT, "BOARD_ADMIN", "203.0.113.5");

  record RenameTankCommand(Caller caller, String name) implements AuditableCommand<String> {
    @Override
    public String auditEntityType() {
      return "tank";
    }

    @Override
    public String auditEntityId(String result) {
      return result;
    }
  }

  record PingCommand() implements Command<String> {}

  private final AuditLogPort auditLog = mock(AuditLogPort.class);
  private final SecurityEventPort events = mock(SecurityEventPort.class);
  private final PrivacyHasherPort hasher = value -> "hmac:" + value;
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);

  @SuppressWarnings("unchecked")
  private <C extends Command<String>> CommandHandler<C, String> handler(
      Class<C> type, Supplier<String> work) {
    CommandHandler<C, String> handler = mock(CommandHandler.class);
    when(handler.handle(any())).thenAnswer(call -> work.get());
    when(handler.commandType()).thenReturn(type);
    return new AuditingCommandHandler<>(handler, auditLog, events, hasher, unitOfWork);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void runTransactionsInline() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
    when(unitOfWork.execute(any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(0)).get());
  }

  @Test
  void aSuccessfulAuditableCommandIsWrittenToTheAuditLog() {
    CommandHandler<RenameTankCommand, String> audited =
        handler(RenameTankCommand.class, () -> "tank-42");

    assertThat(audited.handle(new RenameTankCommand(CALLER, "Nuevo"))).isEqualTo("tank-42");

    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLog).append(entry.capture());
    assertThat(entry.getValue())
        .isEqualTo(
            new AuditEntry(AQUEDUCT, USER, "RENAME_TANK", "tank", "tank-42", "hmac:203.0.113.5"));
    assertThat(audited.commandType()).isEqualTo(RenameTankCommand.class);
  }

  @Test
  void aForbiddenAuditableCommandIsASecurityEventAndNotAnAuditLine() {
    CommandHandler<RenameTankCommand, String> audited =
        handler(
            RenameTankCommand.class,
            () -> {
              throw new DomainException(ErrorCode.FORBIDDEN);
            });

    assertThatThrownBy(() -> audited.handle(new RenameTankCommand(CALLER, "Nuevo")))
        .isInstanceOf(DomainException.class);

    ArgumentCaptor<SecurityEvent> event = ArgumentCaptor.forClass(SecurityEvent.class);
    verify(events).record(event.capture());
    assertThat(event.getValue().type()).isEqualTo(SecurityEventType.PERMISSION_DENIED);
    assertThat(event.getValue().details()).containsEntry("action", "RENAME_TANK");
    verify(auditLog, never()).append(any());
  }

  @Test
  void otherFailuresAndPlainCommandsLeaveNoTrace() {
    CommandHandler<RenameTankCommand, String> failing =
        handler(
            RenameTankCommand.class,
            () -> {
              throw new DomainException(ErrorCode.CONFLICT);
            });
    assertThatThrownBy(() -> failing.handle(new RenameTankCommand(CALLER, "Nuevo")))
        .isInstanceOf(DomainException.class);
    assertThat(handler(PingCommand.class, () -> "pong").handle(new PingCommand()))
        .isEqualTo("pong");

    verifyNoInteractions(auditLog, events);
  }

  @Test
  void namesTheActionAfterTheCommand() {
    assertThat(AuditingCommandHandler.actionOf(new PingCommand())).isEqualTo("PING");
    assertThat(AuditingCommandHandler.actionOf(new RenameTankCommand(CALLER, "x")))
        .isEqualTo("RENAME_TANK");
    assertThat(AuditingCommandHandler.actionOf("text")).isEqualTo("STRING");
  }
}
