package co.caudal.application.device.command;

/**
 * The body of a confirmation of a valve command (docs/API.md, section 16.3).
 *
 * @param result ACKED or FAILED
 * @param detail what the device says about it, or null
 */
public record CommandConfirmation(String result, String detail) {}
