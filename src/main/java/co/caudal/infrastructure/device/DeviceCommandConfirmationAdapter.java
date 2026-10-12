package co.caudal.infrastructure.device;

import co.caudal.application.device.command.CommandConfirmation;
import co.caudal.application.port.out.CommandConfirmationPayloadPort;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.FieldValidationException;
import java.util.Iterator;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapts the JSON of a confirmation of a valve command to the application
 * (docs/Patrones-de-diseno.md, P06). It checks the shape of docs/API.md, section 16.3: {@code
 * result} is required and must be ACKED or FAILED, {@code detail} is optional and has at most 200
 * characters. Unknown fields are refused.
 *
 * @pattern P06 Adapter
 */
@Component
public class DeviceCommandConfirmationAdapter implements CommandConfirmationPayloadPort {

  private static final String RESULT = "result";
  private static final String DETAIL = "detail";
  private static final Set<String> FIELDS = Set.of(RESULT, DETAIL);

  private final JsonMapper json;

  /**
   * Creates the adapter.
   *
   * @param json the application's JSON mapper
   */
  public DeviceCommandConfirmationAdapter(JsonMapper json) {
    this.json = json;
  }

  @Override
  public CommandConfirmation parse(byte[] body) {
    JsonNode root = read(body);
    if (!root.isObject()) {
      throw new FieldValidationException("body", "FIELD_FORMAT_INVALID");
    }
    onlyFields(root);
    return new CommandConfirmation(result(root), detail(root));
  }

  private JsonNode read(byte[] body) {
    try {
      return json.readTree(body);
    } catch (JacksonException malformed) {
      throw new FieldValidationException("body", "FIELD_FORMAT_INVALID");
    }
  }

  private static String result(JsonNode root) {
    JsonNode value = root.get(RESULT);
    if (value == null) {
      throw new FieldValidationException(RESULT, "FIELD_REQUIRED");
    }
    if (!value.isString()) {
      throw new FieldValidationException(RESULT, "FIELD_FORMAT_INVALID");
    }
    if (!value.stringValue().matches(FieldLimits.DEVICE_ACK_RESULT_PATTERN)) {
      throw new FieldValidationException(RESULT, "FIELD_VALUE_NOT_ALLOWED");
    }
    return value.stringValue();
  }

  private static String detail(JsonNode root) {
    JsonNode value = root.get(DETAIL);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isString()) {
      throw new FieldValidationException(DETAIL, "FIELD_FORMAT_INVALID");
    }
    String text = value.stringValue();
    if (text.codePointCount(0, text.length()) > FieldLimits.DEVICE_ACK_DETAIL_MAX) {
      throw new FieldValidationException(DETAIL, "FIELD_TOO_LONG");
    }
    return text;
  }

  private static void onlyFields(JsonNode root) {
    Iterator<String> names = root.propertyNames().iterator();
    while (names.hasNext()) {
      String name = names.next();
      if (!FIELDS.contains(name)) {
        throw new FieldValidationException(name, "FIELD_NOT_ALLOWED");
      }
    }
  }
}
