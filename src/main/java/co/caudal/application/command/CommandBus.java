package co.caudal.application.command;

/**
 * Sends a command to its handler. Controllers talk to the bus, never to a handler, so that every
 * handler can be wrapped (for example to audit it) without the controller knowing.
 *
 * @pattern P13 Command
 */
public interface CommandBus {

  /**
   * Dispatches a command.
   *
   * @param command the command
   * @param <R> what the command answers
   * @return what the handler answered
   * @throws IllegalStateException if no handler is registered for the command
   */
  <R> R dispatch(Command<R> command);
}
