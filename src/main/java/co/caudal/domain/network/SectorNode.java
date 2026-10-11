package co.caudal.domain.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A sector: the composite of the network. It holds its valves and its sub-sectors, and adds up what
 * they serve. An inactive sector serves nobody and opens no valve, and neither do its sub-sectors.
 *
 * @param id the sector
 * @param code its code
 * @param name its name
 * @param active whether it is in use
 * @param ownHouseholds households served directly by this sector
 * @param children its valves and sub-sectors
 * @pattern P08 Composite
 */
public record SectorNode(
    UUID id,
    String code,
    String name,
    boolean active,
    int ownHouseholds,
    List<NetworkNode> children)
    implements NetworkNode {

  /** Copies the children so the tree cannot change. */
  public SectorNode {
    children = List.copyOf(children);
  }

  @Override
  public int households() {
    if (!active) {
      return 0;
    }
    int total = ownHouseholds;
    for (NetworkNode child : children) {
      total += child.households();
    }
    return total;
  }

  @Override
  public List<ValveNode> activeValves() {
    if (!active) {
      return List.of();
    }
    List<ValveNode> valves = new ArrayList<>();
    for (NetworkNode child : children) {
      valves.addAll(child.activeValves());
    }
    return List.copyOf(valves);
  }
}
