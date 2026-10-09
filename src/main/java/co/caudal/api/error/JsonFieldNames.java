package co.caudal.api.error;

import java.util.Locale;
import java.util.regex.Pattern;

/** Converts Java property paths to the snake_case names used in the JSON contract. */
final class JsonFieldNames {

  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

  private JsonFieldNames() {}

  static String toSnakeCase(String path) {
    return CAMEL_BOUNDARY.matcher(path).replaceAll("$1_$2").toLowerCase(Locale.ROOT);
  }
}
