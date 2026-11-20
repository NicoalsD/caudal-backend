package co.caudal.application.autopilot;

/**
 * A valve with its live state, as the database reports it.
 *
 * @param live the state and the last command
 * @param motorized whether the valve can be moved by a command
 * @param active whether the valve is in use
 */
public record ValveRow(LiveValveView live, boolean motorized, boolean active) {}
