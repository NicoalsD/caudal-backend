package co.caudal.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.FieldValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SectorHierarchyTest {

  private static final UUID REGION = UUID.randomUUID();
  private static final UUID MIDDLE = UUID.randomUUID();
  private static final UUID LEAF = UUID.randomUUID();

  private static Map<UUID, UUID> chain() {
    Map<UUID, UUID> parentOf = new HashMap<>();
    parentOf.put(REGION, null);
    parentOf.put(MIDDLE, REGION);
    parentOf.put(LEAF, MIDDLE);
    return parentOf;
  }

  @Test
  void aRootOrAValidParentIsAccepted() {
    SectorHierarchy.requireValidMove(LEAF, null, chain());
    SectorHierarchy.requireValidMove(LEAF, REGION, chain());
  }

  @Test
  void aSectorCannotSitUnderItselfOrItsDescendants() {
    assertThatThrownBy(() -> SectorHierarchy.requireValidMove(REGION, REGION, chain()))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> SectorHierarchy.requireValidMove(REGION, LEAF, chain()))
        .isInstanceOf(FieldValidationException.class);
    assertThatThrownBy(() -> SectorHierarchy.requireValidMove(MIDDLE, LEAF, chain()))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  void anUnknownParentIsAnOrphan() {
    assertThatThrownBy(() -> SectorHierarchy.requireValidMove(LEAF, UUID.randomUUID(), chain()))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  void aTreeIsValidOnlyWithoutCyclesAndOrphans() {
    assertThat(SectorHierarchy.isValidTree(chain())).isTrue();
    Map<UUID, UUID> cycle = chain();
    cycle.put(REGION, LEAF);
    assertThat(SectorHierarchy.isValidTree(cycle)).isFalse();
    Map<UUID, UUID> orphan = chain();
    orphan.put(LEAF, UUID.randomUUID());
    assertThat(SectorHierarchy.isValidTree(orphan)).isFalse();
  }
}
