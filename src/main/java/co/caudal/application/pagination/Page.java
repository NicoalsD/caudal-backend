package co.caudal.application.pagination;

import java.util.List;

/**
 * A page of a list paginated with a signed cursor.
 *
 * @param items the items of the page
 * @param nextCursor cursor of the next page, or null if there are no more
 * @param <T> type of the items
 */
public record Page<T>(List<T> items, String nextCursor) {

  /** Copies the list so the page cannot change. */
  public Page {
    items = List.copyOf(items);
  }
}
