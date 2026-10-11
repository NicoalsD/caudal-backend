package co.caudal.domain.schedule;

import java.util.Map;

/**
 * Why a shift is where it is, as structured data (docs/Reglas-de-negocio.md, section 6).
 *
 * @param code {@code PRIORITY_SECTOR}, {@code HOURS_WITHOUT_SERVICE}, {@code TANK_BAND}, {@code
 *     FORECAST_RANGE}, {@code RESERVE_GUARD} or {@code BOARD_CHANGE}
 * @param params the values of the explanation
 */
public record TurnReason(String code, Map<String, Object> params) {

  /** Freezes the values. */
  public TurnReason {
    params = Map.copyOf(params);
  }
}
