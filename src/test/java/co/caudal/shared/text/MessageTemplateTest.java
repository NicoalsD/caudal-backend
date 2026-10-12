package co.caudal.shared.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageTemplateTest {

  @Test
  void replacesEveryVariable() {
    MessageTemplate template =
        MessageTemplate.parse("{sector} lleva {horas} {unidad_horas} sin servicio.");

    assertThat(template.render(Map.of("sector", "Escuela", "horas", "10", "unidad_horas", "horas")))
        .isEqualTo("Escuela lleva 10 horas sin servicio.");
    assertThat(MessageTemplate.parse("Sin variables.").render(Map.of()))
        .isEqualTo("Sin variables.");
    assertThat(MessageTemplate.parse("{a}{b}").render(Map.of("a", "x", "b", "y"))).isEqualTo("xy");
  }

  @Test
  void aBadTemplateOrAMissingValueIsAnError() {
    assertThatThrownBy(() -> MessageTemplate.parse("Abierto {sin cierre"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MessageTemplate.parse("Vacío {}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MessageTemplate.parse("{sector}").render(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
