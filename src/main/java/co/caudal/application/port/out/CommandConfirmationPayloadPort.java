package co.caudal.application.port.out;

import co.caudal.application.device.command.CommandConfirmation;

/**
 * Reads the body of a confirmation of a valve command. It runs only after the signature is
 * verified, so the body is parsed as the device sent it. The implementation is an adapter that
 * knows the JSON of the devices (docs/Patrones-de-diseno.md, P06).
 */
public interface CommandConfirmationPayloadPort {

  /**
   * Parses the confirmation.
   *
   * @param body the raw body
   * @return the result and the detail
   * @throws co.caudal.shared.error.DomainException with {@code VALIDATION_ERROR} if the body does
   *     not have the shape of the protocol
   */
  CommandConfirmation parse(byte[] body);
}
