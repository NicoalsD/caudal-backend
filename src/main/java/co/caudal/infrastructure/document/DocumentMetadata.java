package co.caudal.infrastructure.document;

/**
 * The top of a document.
 *
 * @param title the title
 * @param subtitle the line under it, or null
 * @param banner a notice above the title, such as "Datos simulados", or null
 */
public record DocumentMetadata(String title, String subtitle, String banner) {}
