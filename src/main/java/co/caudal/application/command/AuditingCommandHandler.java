package co.caudal.application.command;

import co.caudal.application.port.out.AuditLogPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Wraps a command handler and records what security needs to know about it (HU-03, HU-06):
 *
 * <ul>
 *   <li>the success of an {@link AuditableCommand} goes to {@code audit.audit_log}, in the hash
 *       chain of the caller's aqueduct;
 *   <li>a refusal with {@code FORBIDDEN} goes to {@code iam.security_events} as {@code
 *       PERMISSION_DENIED}.
 * </ul>
 *
 * <p>Both records are written in their own transaction, after the handler returned or failed, so a
 * handler that commits part of its work before failing (for example a failed attempt) keeps it. The
 * handler itself is never changed.
 *
 * @param <C> type of the command
 * @param <R> type of the result
 * @pattern P09 Decorator
 */
public final class AuditingCommandHandler<C extends Command<R>, R> implements CommandHandler<C, R> {

  private static final Pattern WORD_START = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
  private static final String COMMAND_SUFFIX = "Command";

  private final CommandHandler<C, R> delegate;
  private final AuditLogPort auditLog;
  private final SecurityEventPort events;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the decorator.
   *
   * @param delegate the handler that does the work
   * @param auditLog the audit log
   * @param events security events
   * @param hasher hides the address of the caller
   * @param unitOfWork transaction boundary of the records
   */
  public AuditingCommandHandler(
      CommandHandler<C, R> delegate,
      AuditLogPort auditLog,
      SecurityEventPort events,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.auditLog = auditLog;
    this.events = events;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public R handle(C command) {
    R result;
    try {
      result = delegate.handle(command);
    } catch (DomainException failure) {
      if (failure.code() == ErrorCode.FORBIDDEN && command instanceof AuditableCommand<?> audited) {
        recordDenied(audited);
      }
      throw failure;
    }
    if (command instanceof AuditableCommand<?> audited) {
      recordSuccess(audited, result);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private void recordSuccess(AuditableCommand<?> command, R result) {
    AuditableCommand<R> typed = (AuditableCommand<R>) command;
    AuditEntry entry =
        new AuditEntry(
            command.caller().aqueductId(),
            command.caller().userId(),
            actionOf(command),
            command.auditEntityType(),
            typed.auditEntityId(result),
            ipHmacOf(command),
            null);
    unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          auditLog.append(entry);
          return null;
        });
  }

  private void recordDenied(AuditableCommand<?> command) {
    SecurityEvent event =
        new SecurityEvent(
            SecurityEventType.PERMISSION_DENIED,
            Severity.MEDIUM,
            command.caller().userId(),
            command.caller().aqueductId(),
            ipHmacOf(command),
            Map.of("action", actionOf(command)));
    unitOfWork.execute(
        () -> {
          events.record(event);
          return null;
        });
  }

  private String ipHmacOf(AuditableCommand<?> command) {
    String address = command.caller().ipAddress();
    return address == null ? null : hasher.hmac(address);
  }

  /** {@code UpdateUserCommand} becomes {@code UPDATE_USER}. */
  static String actionOf(Object command) {
    String name = command.getClass().getSimpleName();
    if (name.endsWith(COMMAND_SUFFIX)) {
      name = name.substring(0, name.length() - COMMAND_SUFFIX.length());
    }
    return WORD_START.matcher(name).replaceAll("_").toUpperCase(Locale.ROOT);
  }

  @Override
  public Class<C> commandType() {
    return delegate.commandType();
  }
}
