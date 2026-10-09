package co.caudal.infrastructure.logging;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Applies {@link LogRedactor} to every string value of the structured (JSON) log lines, including
 * the message, MDC values and stack traces.
 */
public class RedactingJsonMembersCustomizer
    implements StructuredLoggingJsonMembersCustomizer<Object> {

  @Override
  public void customize(JsonWriter.Members<Object> members) {
    members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, LogRedactor::redact));
  }
}
