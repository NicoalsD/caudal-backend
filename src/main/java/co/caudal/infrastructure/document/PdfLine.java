package co.caudal.infrastructure.document;

/**
 * A line of a PDF document.
 *
 * @param text the text
 * @param size font size in points
 * @param bold true for bold
 * @param gapBefore blank points above it
 */
record PdfLine(String text, int size, boolean bold, int gapBefore) {}
