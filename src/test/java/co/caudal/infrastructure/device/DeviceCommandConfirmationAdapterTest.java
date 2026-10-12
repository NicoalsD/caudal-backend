package co.caudal.infrastructure.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.device.command.CommandConfirmation;
import co.caudal.shared.error.FieldValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeviceCommandConfirmationAdapterTest {

  private final DeviceCommandConfirmationAdapter adapter =
      new DeviceCommandConfirmationAdapter(JsonMapper.builder().build());

  private CommandConfirmation parse(String text) {
    return adapter.parse(text.getBytes(StandardCharsets.UTF_8));
  }

  private void assertRefused(String text, String field) {
    assertThatThrownBy(() -> parse(text))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            error -> assertThat(error.details().toString()).contains(field));
  }

  @Test
  void readsTheResultAndTheDetail() {
    CommandConfirmation confirmation =
        parse("{\"result\":\"ACKED\",\"detail\":\"Válvula abierta; posición confirmada.\"}");

    assertThat(confirmation.result()).isEqualTo("ACKED");
    assertThat(confirmation.detail()).isEqualTo("Válvula abierta; posición confirmada.");
  }

  @Test
  void theDetailIsOptional() {
    assertThat(parse("{\"result\":\"FAILED\"}").detail()).isNull();
    assertThat(parse("{\"result\":\"FAILED\",\"detail\":null}").detail()).isNull();
    assertThat(parse("{\"result\":\"FAILED\",\"detail\":\"\"}").detail()).isEmpty();
  }

  @Test
  void theResultIsAcknowledgedOrFailedOnly() {
    assertRefused("{\"detail\":\"sin resultado\"}", "result");
    assertRefused("{\"result\":\"DELIVERED\"}", "result");
    assertRefused("{\"result\":\"acked\"}", "result");
    assertRefused("{\"result\":1}", "result");
  }

  @Test
  void theDetailHasAtMost200Characters() {
    String exact = "a".repeat(200);

    assertThat(parse("{\"result\":\"ACKED\",\"detail\":\"" + exact + "\"}").detail())
        .isEqualTo(exact);
    assertRefused("{\"result\":\"ACKED\",\"detail\":\"" + "a".repeat(201) + "\"}", "detail");
  }

  @Test
  void unknownFieldsAndWrongShapesAreRefused() {
    assertRefused("{\"result\":\"ACKED\",\"status\":\"ACKED\"}", "status");
    assertRefused("[\"ACKED\"]", "body");
    assertRefused("{\"result\":", "body");
  }
}
