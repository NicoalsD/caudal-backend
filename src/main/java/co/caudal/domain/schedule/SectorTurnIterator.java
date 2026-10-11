package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Walks the sectors in the order they get water (docs/Reglas-de-negocio.md, section 5.1): first the
 * priority sectors by {@code priority_rank}, then the rest by hours without service, longest wait
 * first, with the code as tie breaker. Sectors the rules exclude are never visited, and in a band
 * marked {@code priority_only} only the priority sectors are.
 *
 * @pattern P14 Iterator
 */
public final class SectorTurnIterator implements Iterator<SectorCandidate> {

  private static final Comparator<SectorCandidate> PRIORITY_ORDER =
      Comparator.comparing(
              SectorCandidate::priorityRank, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(SectorCandidate::code);

  private static final Comparator<SectorCandidate> WAIT_ORDER =
      Comparator.comparing(
              SectorCandidate::hoursWithoutService,
              Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
          .thenComparing(SectorCandidate::code);

  private final List<SectorCandidate> order;
  private int next;

  /**
   * Creates the iterator.
   *
   * @param candidates every sector of the aqueduct
   * @param priorityOnly true when only priority sectors get water
   */
  public SectorTurnIterator(List<SectorCandidate> candidates, boolean priorityOnly) {
    List<SectorCandidate> priority = new ArrayList<>();
    List<SectorCandidate> rest = new ArrayList<>();
    for (SectorCandidate candidate : candidates) {
      if (!candidate.included()) {
        continue;
      }
      if (candidate.priority()) {
        priority.add(candidate);
      } else if (!priorityOnly) {
        rest.add(candidate);
      }
    }
    priority.sort(PRIORITY_ORDER);
    rest.sort(WAIT_ORDER);
    List<SectorCandidate> all = new ArrayList<>(priority);
    all.addAll(rest);
    this.order = List.copyOf(all);
  }

  @Override
  public boolean hasNext() {
    return next < order.size();
  }

  @Override
  public SectorCandidate next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return order.get(next++);
  }

  /**
   * How many sectors it visits.
   *
   * @return the number of sectors in the walk
   */
  public int size() {
    return order.size();
  }
}
