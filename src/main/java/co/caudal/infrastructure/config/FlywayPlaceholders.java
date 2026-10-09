package co.caudal.infrastructure.config;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.PhysicalConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feeds the Flyway placeholders from {@link FieldLimits} and {@link PhysicalConstants}.
 *
 * <p>Every public constant becomes a placeholder with its name in lower case, so {@code
 * FieldLimits.PERSON_NAME_MAX} is {@code ${person_name_max}} in the migrations. The database and
 * the API therefore use the same number, and the drift tests compare both through {@link
 * #values()}.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayPlaceholders {

  private static final Class<?>[] SOURCES = {FieldLimits.class, PhysicalConstants.class};

  @Bean
  FlywayConfigurationCustomizer fieldLimitsPlaceholders() {
    return configuration -> configuration.placeholders(values());
  }

  /**
   * Returns the placeholder map: lower case constant name to its value as text.
   *
   * @return an ordered, unmodifiable map of placeholders
   */
  public static Map<String, String> values() {
    Map<String, String> placeholders = new TreeMap<>();
    for (Class<?> source : SOURCES) {
      for (Field field : source.getFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
          placeholders.put(field.getName().toLowerCase(Locale.ROOT), read(field));
        }
      }
    }
    return Map.copyOf(placeholders);
  }

  private static String read(Field field) {
    try {
      return String.valueOf(field.get(null));
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Cannot read constant " + field.getName(), ex);
    }
  }
}
