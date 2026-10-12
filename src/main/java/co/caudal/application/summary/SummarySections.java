package co.caudal.application.summary;

/**
 * How many records of a period fall in each section of the minutes (docs/Reglas-de-negocio.md,
 * section 16.1). Only the counts leave the system: never the text of a record.
 *
 * @param observed records seen or measured
 * @param estimated records calculated
 * @param inferred records suspected
 * @param confirmed records verified in the field
 */
public record SummarySections(int observed, int estimated, int inferred, int confirmed) {}
