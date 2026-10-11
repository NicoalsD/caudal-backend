package co.caudal.api.controller;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Technical limits of the fields, from {@link FieldLimits}, so the web app validates with the same
 * numbers as the API (docs/API.md, section 4.1). Business parameters are not here: they live in the
 * rules of the Board ({@code GET /rule-sets/current}).
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta y catálogos", description = "Límites de los campos y catálogos públicos.")
public class MetaController {

  private static final String MIN = "min";
  private static final String MAX = "max";
  private static final String PATTERN = "pattern";
  private static final Duration CACHE = Duration.ofHours(1);

  private static final Map<String, Map<String, Object>> CONSTRAINTS = build();

  @Operation(
      summary = "Límites de los campos",
      description =
          "Longitudes, patrones y topes técnicos de FieldLimits, la misma fuente que usan Bean"
              + " Validation y la base de datos, para que el frontend arme sus esquemas. Es"
              + " público y no tiene datos del acueducto.")
  @ApiResponse(responseCode = "200", description = "Límites por campo")
  @GetMapping("/constraints")
  public ResponseEntity<Map<String, Map<String, Object>>> constraints() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(CACHE).cachePublic())
        .body(CONSTRAINTS);
  }

  private static Map<String, Map<String, Object>> build() {
    Map<String, Map<String, Object>> c = new LinkedHashMap<>();
    c.put(
        "username",
        Map.of(
            MIN,
            FieldLimits.USERNAME_MIN,
            MAX,
            FieldLimits.USERNAME_MAX,
            PATTERN,
            FieldLimits.USERNAME_PATTERN));
    c.put("password", Map.of(MIN, FieldLimits.PASSWORD_MIN, MAX, FieldLimits.PASSWORD_MAX));
    c.put(
        "person_name",
        Map.of(
            MIN,
            FieldLimits.PERSON_NAME_MIN,
            MAX,
            FieldLimits.PERSON_NAME_MAX,
            PATTERN,
            FieldLimits.PERSON_NAME_PATTERN));
    c.put(
        "place_name",
        Map.of(
            MIN,
            FieldLimits.PLACE_NAME_MIN,
            MAX,
            FieldLimits.PLACE_NAME_MAX,
            PATTERN,
            FieldLimits.PLACE_NAME_PATTERN));
    c.put(
        "sector.code",
        Map.of(
            MIN,
            FieldLimits.SECTOR_CODE_MIN,
            MAX,
            FieldLimits.SECTOR_CODE_MAX,
            PATTERN,
            FieldLimits.SECTOR_CODE_PATTERN));
    c.put(
        "valve.code",
        Map.of(
            MIN,
            FieldLimits.VALVE_CODE_MIN,
            MAX,
            FieldLimits.VALVE_CODE_MAX,
            PATTERN,
            FieldLimits.VALVE_CODE_PATTERN));
    c.put("valve.location_hint", Map.of(MIN, 0, MAX, FieldLimits.LOCATION_HINT_MAX));
    c.put(
        "reading.note",
        Map.of(MIN, 0, MAX, FieldLimits.NOTE_MAX, "max_lines", FieldLimits.NOTE_MAX_LINES));
    c.put(
        "reading.gauge_value",
        Map.of(
            "integer_digits", FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
            "fraction_digits", FieldLimits.GAUGE_DECIMALS_MAX,
            "future_skew_seconds", FieldLimits.FUTURE_TIMESTAMP_SKEW_SECONDS));
    c.put("reason", Map.of(MIN, FieldLimits.REASON_MIN, MAX, FieldLimits.REASON_MAX));
    c.put(
        "incident.description",
        Map.of(
            MIN, FieldLimits.INCIDENT_DESCRIPTION_MIN, MAX, FieldLimits.INCIDENT_DESCRIPTION_MAX));
    c.put(
        "catalog.code",
        Map.of(
            MIN,
            FieldLimits.CATALOG_CODE_MIN,
            MAX,
            FieldLimits.CATALOG_CODE_MAX,
            PATTERN,
            FieldLimits.CATALOG_CODE_PATTERN));
    c.put(
        "tracking_code",
        Map.of(
            MIN, FieldLimits.TRACKING_CODE_LENGTH,
            MAX, FieldLimits.TRACKING_CODE_LENGTH,
            PATTERN, FieldLimits.TRACKING_CODE_PATTERN));
    c.put(
        "reset_code",
        Map.of(MIN, FieldLimits.RESET_CODE_LENGTH, MAX, FieldLimits.RESET_CODE_INPUT_MAX));
    c.put(
        "mfa_code",
        Map.of(
            MIN,
            FieldLimits.TOTP_DIGITS,
            MAX,
            FieldLimits.TOTP_DIGITS,
            PATTERN,
            FieldLimits.TOTP_CODE_PATTERN));
    c.put(
        "aqueduct.slug",
        Map.of(
            MIN,
            FieldLimits.AQUEDUCT_SLUG_MIN,
            MAX,
            FieldLimits.AQUEDUCT_SLUG_MAX,
            PATTERN,
            FieldLimits.AQUEDUCT_SLUG_PATTERN));
    c.put(
        "page.limit",
        Map.of(
            MIN,
            FieldLimits.PAGE_LIMIT_MIN,
            MAX,
            FieldLimits.PAGE_LIMIT_MAX,
            "default",
            FieldLimits.PAGE_LIMIT_DEFAULT));
    c.put("list.items", Map.of(MIN, 0, MAX, FieldLimits.LIST_MAX_ITEMS));
    c.put("rule_set.bands", Map.of(MIN, 1, MAX, FieldLimits.RULE_BANDS_MAX));
    c.put(
        "forecast.horizon_days",
        Map.of(
            MIN,
            FieldLimits.FORECAST_HORIZON_DAYS_MIN,
            MAX,
            FieldLimits.FORECAST_HORIZON_DAYS_MAX));
    c.put("text", Map.of(MIN, 0, MAX, FieldLimits.TEXT_GLOBAL_MAX));
    c.put("body.bytes", Map.of(MIN, 0, MAX, FieldLimits.BODY_MAX_BYTES));
    return Collections.unmodifiableMap(c);
  }
}
