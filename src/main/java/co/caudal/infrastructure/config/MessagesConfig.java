package co.caudal.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Loads the user facing texts from {@code messages_es.properties}.
 *
 * <p>Declared explicitly because the bundle has no default {@code messages.properties}: Spanish is
 * the only language and the system locale is never used as a fallback.
 */
@Configuration(proxyBeanMethods = false)
public class MessagesConfig {

  /** Base name of the message bundle. */
  public static final String BASENAME = "messages";

  /** Language of every user facing text. */
  public static final Locale LOCALE = Locale.forLanguageTag("es");

  @Bean
  MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename(BASENAME);
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setDefaultLocale(LOCALE);
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
