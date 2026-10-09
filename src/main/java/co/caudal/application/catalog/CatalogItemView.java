package co.caudal.application.catalog;

/**
 * An option of a list.
 *
 * @param code the code in English
 * @param labelEs the label shown to people
 * @param sortOrder the order
 */
public record CatalogItemView(String code, String labelEs, int sortOrder) {}
