package co.caudal.application.command;

/**
 * Marker of a command: a request to change something, as an object that can be validated, audited
 * and dispatched (docs/Patrones-de-diseno.md, P13).
 *
 * @param <R> what the command answers; {@link Done} when there is nothing to answer
 */
public interface Command<R> {}
