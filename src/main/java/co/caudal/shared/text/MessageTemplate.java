package co.caudal.shared.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A small language for the Spanish texts built from structured data (docs/Reglas-de-negocio.md,
 * section 6): literal text and variables written as {@code {name}}. The template is parsed once
 * into an expression of literal and variable parts, and interpreting it with the values gives the
 * text; a missing value is an error, so a text never shows a raw placeholder.
 *
 * @pattern P22 Interpreter
 */
public final class MessageTemplate {

  /** One part of a template. */
  private sealed interface Part permits Literal, Variable {
    String interpret(Map<String, String> values);
  }

  private record Literal(String text) implements Part {
    @Override
    public String interpret(Map<String, String> values) {
      return text;
    }
  }

  private record Variable(String name) implements Part {
    @Override
    public String interpret(Map<String, String> values) {
      String value = values.get(name);
      if (value == null) {
        throw new IllegalArgumentException("Missing value for {" + name + "}");
      }
      return value;
    }
  }

  private final List<Part> parts;

  private MessageTemplate(List<Part> parts) {
    this.parts = List.copyOf(parts);
  }

  /**
   * Parses a template.
   *
   * @param template the text with {@code {name}} variables
   * @return the parsed template
   * @throws IllegalArgumentException if a brace is not closed or a name is empty
   */
  public static MessageTemplate parse(String template) {
    Objects.requireNonNull(template, "template");
    List<Part> parts = new ArrayList<>();
    int position = 0;
    while (position < template.length()) {
      int open = template.indexOf('{', position);
      if (open < 0) {
        parts.add(new Literal(template.substring(position)));
        break;
      }
      int close = template.indexOf('}', open);
      if (close < 0 || close == open + 1) {
        throw new IllegalArgumentException("Bad variable in template");
      }
      if (open > position) {
        parts.add(new Literal(template.substring(position, open)));
      }
      parts.add(new Variable(template.substring(open + 1, close)));
      position = close + 1;
    }
    return new MessageTemplate(parts);
  }

  /**
   * Interprets the template with values.
   *
   * @param values the text of each variable
   * @return the text
   * @throws IllegalArgumentException if a variable has no value
   */
  public String render(Map<String, String> values) {
    StringBuilder text = new StringBuilder();
    for (Part part : parts) {
      text.append(part.interpret(values));
    }
    return text.toString();
  }
}
