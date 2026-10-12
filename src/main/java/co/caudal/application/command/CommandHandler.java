package co.caudal.application.command;

/**
 * Carries out one kind of command. The answer type is part of the contract, so a command can return
 * what the caller needs (for example a code that is shown only once).
 *
 * @param <C> the command it handles
 * @param <R> what it answers
 */
public interface CommandHandler<C extends Command<R>, R> {

  /**
   * Carries out the command.
   *
   * @param command the command
   * @return the answer
   */
  R handle(C command);

  /**
   * Tells which command this handler is for, so the bus can route it.
   *
   * @return the command class
   */
  Class<C> commandType();
}
