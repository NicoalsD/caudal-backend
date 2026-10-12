package co.caudal.infrastructure.device;

import co.caudal.application.device.telemetry.TelemetryPoint;
import co.caudal.application.port.out.TelemetryPayloadPort;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.FieldValidationException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapts the JSON of a telemetry request of a device to the points of the application
 * (docs/Patrones-de-diseno.md, P06). It checks the shape the protocol defines (docs/API.md, section
 * 16.1): the list of 1 to 100 points, the fields and their formats. Unknown fields are refused.
 *
 * @pattern P06 Adapter
 */
@Component
public class DeviceTelemetryAdapter implements TelemetryPayloadPort {

  private static final String POINTS = "points";
  private static final String ID = "id";
  private static final String METRIC = "metric";
  private static final String VALUE = "value";
  private static final String OBSERVED_AT = "observed_at";
  private static final Set<String> POINT_FIELDS = Set.of(ID, METRIC, VALUE, OBSERVED_AT);
  private static final BigDecimal VALUE_LIMIT =
      BigDecimal.TEN.pow(FieldLimits.TELEMETRY_VALUE_INTEGER_DIGITS_MAX);

  private final JsonMapper json;

  /**
   * Creates the adapter.
   *
   * @param json the application's JSON mapper
   */
  public DeviceTelemetryAdapter(JsonMapper json) {
    this.json = json;
  }

  @Override
  public List<TelemetryPoint> parse(byte[] body) {
    JsonNode root = read(body);
    if (!root.isObject()) {
      throw new FieldValidationException("body", "FIELD_FORMAT_INVALID");
    }
    onlyFields(root, Set.of(POINTS), "body");
    JsonNode points = root.get(POINTS);
    if (points == null) {
      throw new FieldValidationException(POINTS, "FIELD_REQUIRED");
    }
    if (!points.isArray()) {
      throw new FieldValidationException(POINTS, "FIELD_FORMAT_INVALID");
    }
    if (points.size() < 1 || points.size() > FieldLimits.TELEMETRY_BATCH_MAX) {
      throw new FieldValidationException(POINTS, "FIELD_OUT_OF_RANGE");
    }
    List<TelemetryPoint> parsed = new ArrayList<>(points.size());
    Iterator<JsonNode> items = points.iterator();
    for (int index = 0; items.hasNext(); index++) {
      parsed.add(point(items.next(), POINTS + "[" + index + "]"));
    }
    return parsed;
  }

  private JsonNode read(byte[] body) {
    try {
      return json.readTree(body);
    } catch (JacksonException malformed) {
      throw new FieldValidationException("body", "FIELD_FORMAT_INVALID");
    }
  }

  private static TelemetryPoint point(JsonNode node, String path) {
    if (!node.isObject()) {
      throw new FieldValidationException(path, "FIELD_FORMAT_INVALID");
    }
    onlyFields(node, POINT_FIELDS, path);
    UUID id = UUID.fromString(text(node, ID, path, FieldLimits.DEVICE_ID_PATTERN));
    String metric = text(node, METRIC, path, FieldLimits.TELEMETRY_METRIC_PATTERN);
    BigDecimal value = number(node, path);
    return new TelemetryPoint(id, metric, value, instant(node, path));
  }

  private static String text(JsonNode node, String field, String path, String pattern) {
    JsonNode value = required(node, field, path);
    if (!value.isString() || !value.stringValue().matches(pattern)) {
      throw new FieldValidationException(path + "." + field, "FIELD_FORMAT_INVALID");
    }
    return value.stringValue();
  }

  private static BigDecimal number(JsonNode node, String path) {
    JsonNode value = required(node, VALUE, path);
    if (!value.isNumber()) {
      throw new FieldValidationException(path + "." + VALUE, "FIELD_FORMAT_INVALID");
    }
    BigDecimal decimal = value.decimalValue();
    if (decimal.stripTrailingZeros().scale() > FieldLimits.TELEMETRY_VALUE_DECIMALS_MAX) {
      throw new FieldValidationException(path + "." + VALUE, "FIELD_FORMAT_INVALID");
    }
    if (decimal.abs().compareTo(VALUE_LIMIT) >= 0) {
      throw new FieldValidationException(path + "." + VALUE, "FIELD_OUT_OF_RANGE");
    }
    return decimal;
  }

  /** The moment, with zone (ISO-8601), at microsecond precision like the database. */
  private static java.time.Instant instant(JsonNode node, String path) {
    String text = text(node, OBSERVED_AT, path, ".+");
    try {
      return OffsetDateTime.parse(text).toInstant().truncatedTo(ChronoUnit.MICROS);
    } catch (DateTimeException notAMoment) {
      throw new FieldValidationException(path + "." + OBSERVED_AT, "FIELD_FORMAT_INVALID");
    }
  }

  private static JsonNode required(JsonNode node, String field, String path) {
    JsonNode value = node.get(field);
    if (value == null) {
      throw new FieldValidationException(path + "." + field, "FIELD_REQUIRED");
    }
    return value;
  }

  private static void onlyFields(JsonNode node, Set<String> allowed, String path) {
    Iterator<String> names = node.propertyNames().iterator();
    while (names.hasNext()) {
      String name = names.next();
      if (!allowed.contains(name)) {
        throw new FieldValidationException(path + "." + name, "FIELD_NOT_ALLOWED");
      }
    }
  }
}
