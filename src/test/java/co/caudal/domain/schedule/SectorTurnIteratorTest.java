package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SectorTurnIteratorTest {

  static SectorCandidate sector(String code, boolean priority, Integer rank, String hours) {
    return new SectorCandidate(
        UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        code,
        "Sector " + code,
        true,
        priority,
        rank,
        new BigDecimal(hours),
        "CONFIRMED");
  }

  static final List<SectorCandidate> EXAMPLE =
      List.of(
          sector("ALTO", false, null, "16"),
          sector("SUR", false, null, "22"),
          sector("ESC", true, 1, "10"),
          sector("CENTRO", false, null, "32"));

  private static List<String> walk(SectorTurnIterator iterator) {
    List<String> codes = new ArrayList<>();
    iterator.forEachRemaining(c -> codes.add(c.code()));
    return codes;
  }

  @Test
  void prioritySectorsGoFirstThenTheLongestWait() {
    assertThat(walk(new SectorTurnIterator(EXAMPLE, false)))
        .containsExactly("ESC", "CENTRO", "SUR", "ALTO");
  }

  @Test
  void aPriorityOnlyBandVisitsOnlyPrioritySectors() {
    SectorTurnIterator iterator = new SectorTurnIterator(EXAMPLE, true);

    assertThat(iterator.size()).isEqualTo(1);
    assertThat(walk(iterator)).containsExactly("ESC");
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void excludedSectorsAreSkippedAndTiesGoByCode() {
    SectorCandidate excluded =
        new SectorCandidate(
            UUID.randomUUID(), "AAA", "Fuera", false, false, null, BigDecimal.TEN, "CONFIRMED");
    List<SectorCandidate> sectors =
        List.of(sector("ZETA", false, null, "5"), sector("BETA", false, null, "5"), excluded);

    assertThat(walk(new SectorTurnIterator(sectors, false))).containsExactly("BETA", "ZETA");
  }
}
