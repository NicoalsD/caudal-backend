package co.caudal.domain.network;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.network.NetworkTree.SectorRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkNodeTest {

  private static final UUID NORTH = UUID.randomUUID();
  private static final UUID NORTH_A = UUID.randomUUID();
  private static final UUID SOUTH = UUID.randomUUID();

  private static ValveNode valve(String code, boolean active) {
    return new ValveNode(UUID.randomUUID(), code, "Válvula " + code, active, false);
  }

  @Test
  void aSectorAddsUpWhatItsSubSectorsServeAndOpen() {
    ValveNode v1 = valve("V1", true);
    ValveNode v2 = valve("V2", true);
    ValveNode off = valve("V3", false);
    List<SectorNode> roots =
        NetworkTree.build(
            List.of(
                new SectorRow(NORTH, "NORTE", "Norte", null, 10, true),
                new SectorRow(NORTH_A, "NORTE-A", "Norte A", NORTH, 5, true),
                new SectorRow(SOUTH, "SUR", "Sur", null, 8, false)),
            Map.of(
                NORTH, List.of(v1, off), NORTH_A, List.of(v2), SOUTH, List.of(valve("V4", true))));

    SectorNode north = roots.getFirst();
    assertThat(roots).extracting(NetworkNode::code).containsExactly("NORTE", "SUR");
    assertThat(north.households()).isEqualTo(15);
    assertThat(north.activeValves()).containsExactly(v1, v2);
    assertThat(north.children()).hasSize(3);
    assertThat(roots.get(1).households()).isZero();
    assertThat(roots.get(1).activeValves()).isEmpty();
    assertThat(v1.children()).isEmpty();
    assertThat(v1.households()).isZero();
    assertThat(off.activeValves()).isEmpty();
  }

  @Test
  void anOrphanOrACycleBecomesATopSector() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    List<SectorNode> roots =
        NetworkTree.build(
            List.of(
                new SectorRow(a, "A", "A", b, 1, true),
                new SectorRow(b, "B", "B", a, 1, true),
                new SectorRow(NORTH, "HUERFANO", "Huérfano", UUID.randomUUID(), 2, true)),
            Map.of());

    assertThat(roots).extracting(NetworkNode::code).containsExactly("A", "B", "HUERFANO");
    assertThat(roots.stream().mapToInt(NetworkNode::households).sum()).isEqualTo(4);
  }
}
