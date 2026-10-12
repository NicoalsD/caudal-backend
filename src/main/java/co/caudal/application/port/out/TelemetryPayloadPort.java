package co.caudal.application.port.out;

import co.caudal.application.device.telemetry.TelemetryPoint;
import java.util.List;

/**
 * Reads the body of a telemetry request. It runs only after the signature is verified, so the body
 * is parsed as the device sent it. The implementation is an adapter that knows the JSON of the
 * devices (docs/Patrones-de-diseno.md, P06).
 */
public interface TelemetryPayloadPort {

  /**
   * Parses the points of a batch.
   *
   * @param body the raw body
   * @return the points, in the order received, from 1 to 100
   * @throws co.caudal.shared.error.DomainException with {@code VALIDATION_ERROR} if the body does
   *     not have the shape of the protocol
   */
  List<TelemetryPoint> parse(byte[] body);
}
