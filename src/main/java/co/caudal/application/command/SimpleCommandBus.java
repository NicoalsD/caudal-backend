package co.caudal.application.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Command bus that routes by the class of the command. There is exactly one handler per command: a
 * second one for the same command is a wiring mistake and fails at startup.
 *
 * @pattern P13 Command
 */
public final class SimpleCommandBus implements CommandBus {

  private final Map<Class<?>, CommandHandler<?, ?>> handlers;

  /**
   * Creates the bus.
   *
   * @param handlers every handler; each one registers under its {@link
   *     CommandHandler#commandType()}
   * @throws IllegalArgumentException if two handlers claim the same command
   */
  public SimpleCommandBus(Collection<? extends CommandHandler<?, ?>> handlers) {
    Map<Class<?>, CommandHandler<?, ?>> byCommand = new HashMap<>();
    for (CommandHandler<?, ?> handler : Objects.requireNonNull(handlers, "handlers")) {
      CommandHandler<?, ?> previous = byCommand.put(handler.commandType(), handler);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Two handlers for " + handler.commandType().getSimpleName());
      }
    }
    this.handlers = Map.copyOf(byCommand);
  }

  @Override
  public <R> R dispatch(Command<R> command) {
    Objects.requireNonNull(command, "command");
    CommandHandler<?, ?> handler = handlers.get(command.getClass());
    if (handler == null) {
      throw new IllegalStateException("No handler for " + command.getClass().getSimpleName());
    }
    return handle(handler, command);
  }

  // The map is keyed by the command class, so the handler found accepts exactly this command.
  @SuppressWarnings("unchecked")
  private static <C extends Command<R>, R> R handle(CommandHandler<?, ?> handler, Command<R> c) {
    return ((CommandHandler<C, R>) handler).handle((C) c);
  }
}
