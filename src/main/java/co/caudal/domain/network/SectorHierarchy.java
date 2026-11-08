package co.caudal.domain.network;

import co.caudal.shared.error.FieldValidationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of the tree of sectors. A region is a root sector (a sector without parent) and the
 * sectors under it belong to it. Whatever the board edits, the tree has no cycles and no orphans: a
 * sector never sits under itself or under one of its descendants, and its parent exists in the same
 * aqueduct.
 *
 * @pattern P08 Composite
 */
public final class SectorHierarchy {

  private static final String FIELD = "parent_sector_id";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private SectorHierarchy() {}

  /**
   * Checks that moving a sector under a new parent keeps the tree valid.
   *
   * @param sectorId the sector being moved
   * @param newParentId the new parent, or null to make the sector a region (a root)
   * @param parentOf the parent of every sector of the aqueduct, null for the roots
   * @throws FieldValidationException if the parent is unknown (orphan), is the sector itself, or is
   *     one of its descendants (cycle)
   */
  public static void requireValidMove(UUID sectorId, UUID newParentId, Map<UUID, UUID> parentOf) {
    if (newParentId == null) {
      return;
    }
    if (newParentId.equals(sectorId) || !parentOf.containsKey(newParentId)) {
      throw new FieldValidationException(FIELD, NOT_ALLOWED);
    }
    Map<UUID, UUID> after = new HashMap<>(parentOf);
    after.put(sectorId, newParentId);
    if (!isValidTree(after)) {
      throw new FieldValidationException(FIELD, NOT_ALLOWED);
    }
  }

  /**
   * Tells whether a tree of sectors has no cycles and no orphans.
   *
   * @param parentOf the parent of every sector, null for the roots
   * @return true if every sector reaches a root through existing parents
   */
  public static boolean isValidTree(Map<UUID, UUID> parentOf) {
    for (UUID start : parentOf.keySet()) {
      Set<UUID> seen = new HashSet<>();
      UUID current = start;
      while (current != null) {
        if (!parentOf.containsKey(current) || !seen.add(current)) {
          return false;
        }
        current = parentOf.get(current);
      }
    }
    return true;
  }
}
