package co.caudal.domain.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds the tree of {@link NetworkNode} of an aqueduct from its flat rows. */
public final class NetworkTree {

  /**
   * A sector row.
   *
   * @param id the sector
   * @param code its code
   * @param name its name
   * @param parentId its parent sector, or null for a top sector
   * @param households households it serves directly
   * @param active whether it is in use
   */
  public record SectorRow(
      UUID id, String code, String name, UUID parentId, int households, boolean active) {}

  private NetworkTree() {}

  /**
   * Builds the top sectors with everything under them. A sector whose parent is unknown, or that
   * would close a cycle, is treated as a top sector so no part of the network is lost.
   *
   * @param sectors every sector, in display order
   * @param valvesBySector the valves of each sector, in display order
   * @return the top sectors
   */
  public static List<SectorNode> build(
      List<SectorRow> sectors, Map<UUID, List<ValveNode>> valvesBySector) {
    Map<UUID, SectorRow> byId = new LinkedHashMap<>();
    for (SectorRow row : sectors) {
      byId.put(row.id(), row);
    }
    Map<UUID, List<SectorRow>> childrenOf = new LinkedHashMap<>();
    List<SectorRow> roots = new ArrayList<>();
    for (SectorRow row : sectors) {
      if (row.parentId() == null || !byId.containsKey(row.parentId()) || closesCycle(row, byId)) {
        roots.add(row);
      } else {
        childrenOf.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(row);
      }
    }
    return roots.stream().map(root -> node(root, childrenOf, valvesBySector)).toList();
  }

  private static boolean closesCycle(SectorRow row, Map<UUID, SectorRow> byId) {
    Set<UUID> seen = new HashSet<>();
    UUID current = row.id();
    while (current != null && byId.containsKey(current)) {
      if (!seen.add(current)) {
        return true;
      }
      current = byId.get(current).parentId();
    }
    return false;
  }

  private static SectorNode node(
      SectorRow row,
      Map<UUID, List<SectorRow>> childrenOf,
      Map<UUID, List<ValveNode>> valvesBySector) {
    List<NetworkNode> children = new ArrayList<>(valvesBySector.getOrDefault(row.id(), List.of()));
    for (SectorRow child : childrenOf.getOrDefault(row.id(), List.of())) {
      children.add(node(child, childrenOf, valvesBySector));
    }
    return new SectorNode(
        row.id(), row.code(), row.name(), row.active(), row.households(), children);
  }
}
