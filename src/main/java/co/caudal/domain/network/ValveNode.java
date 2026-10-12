package co.caudal.domain.network;

import java.util.List;
import java.util.UUID;

/**
 * A valve: the leaf of the network.
 *
 * @param id the valve
 * @param code its code
 * @param name its name
 * @param active whether it is in use
 * @param motorized whether a device can move it
 * @pattern P08 Composite
 */
public record ValveNode(UUID id, String code, String name, boolean active, boolean motorized)
    implements NetworkNode {

  @Override
  public List<NetworkNode> children() {
    return List.of();
  }

  @Override
  public int households() {
    return 0;
  }

  @Override
  public List<ValveNode> activeValves() {
    return active ? List.of(this) : List.of();
  }
}
