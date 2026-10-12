package co.caudal.application.simulation;

/**
 * A row of an import that was refused.
 *
 * @param row its position in the file, from 1
 * @param code why, for example {@code GAUGE_OUT_OF_RANGE}
 */
public record ImportRejection(int row, String code) {}
