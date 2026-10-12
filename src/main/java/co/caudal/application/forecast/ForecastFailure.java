package co.caudal.application.forecast;

/**
 * Why the forecast service did not give a usable forecast (docs/Contrato-IA.md, section 3.4).
 *
 * @param reason the {@code fallback_reason} stored with the replacement: {@code IA_TIMEOUT}, {@code
 *     IA_ERROR} or {@code INSUFFICIENT_HISTORY}
 * @param countsForCircuit true if the failure says the service is unwell (timeouts, network errors,
 *     5xx, 401, invalid answers); false for answers about the data
 * @param detail a short technical detail for the logs, never with data of the series
 */
public record ForecastFailure(String reason, boolean countsForCircuit, String detail) {

  /** The service did not answer in time. */
  public static final String IA_TIMEOUT = "IA_TIMEOUT";

  /** The service failed or answered something unusable. */
  public static final String IA_ERROR = "IA_ERROR";

  /** The tank has too little history to forecast. */
  public static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
}
