package co.caudal.infrastructure.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.device.telemetry.TelemetryPoint;
import co.caudal.shared.error.FieldValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeviceTelemetryAdapterTest {

  private static final String ID = "0190f3a2-6666-7000-8000-000000000001";
  private static final String POINT =
      "{\"id\":\""
          + ID
          + "\",\"metric\":\"LEVEL\",\"value\":2.10,"
          + "\"observed_at\":\"2026-10-08T18:00:00.123456789-05:00\"}";

  private final DeviceTelemetryAdapter adapter =
      new DeviceTelemetryAdapter(JsonMapper.builder().build());

  private static byte[] body(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String batch(String... points) {
    return "{\"points\":[" + String.join(",", points) + "]}";
  }

  @Test
  void readsEveryFieldOfAValidPointAtMicrosecondPrecision() {
    List<TelemetryPoint> points = adapter.parse(body(batch(POINT)));

    assertThat(points).hasSize(1);
    TelemetryPoint point = points.getFirst();
    assertThat(point.id().toString()).isEqualTo(ID);
    assertThat(point.metric()).isEqualTo("LEVEL");
    assertThat(point.value()).isEqualByComparingTo(new BigDecimal("2.10"));
    assertThat(point.observedAt()).isEqualTo(Instant.parse("2026-10-08T23:00:00.123456Z"));
  }

  @Test
  void aBatchHasBetweenOneAndOneHundredPoints() {
    String one = batch(POINT);
    String hundred = batch(String.join(",", java.util.Collections.nCopies(100, POINT)));
    String many = batch(String.join(",", java.util.Collections.nCopies(101, POINT)));

    assertThat(adapter.parse(body(one))).hasSize(1);
    assertThat(adapter.parse(body(hundred))).hasSize(100);
    assertRefused(batch(), "points");
    assertRefused(many, "points");
  }

  @Test
  void unknownFieldsAreRefused() {
    assertRefused(batch(POINT.replace("}", ",\"extra\":1}")), "points[0].extra");
    assertRefused("{\"points\":[" + POINT + "],\"other\":1}", "body.other");
  }

  @Test
  void aBadMetricOrIdIsRefused() {
    assertRefused(batch(POINT.replace("LEVEL", "TEMPERATURE")), "points[0].metric");
    assertRefused(batch(POINT.replace(ID, "no-es-uuid")), "points[0].id");
  }

  @Test
  void aValueWithMoreThanThreeDecimalsOrTooBigIsRefused() {
    assertRefused(batch(POINT.replace("2.10", "2.1234")), "points[0].value");
    assertRefused(batch(POINT.replace("2.10", "12345678")), "points[0].value");
    assertRefused(batch(POINT.replace("2.10", "\"2.10\"")), "points[0].value");
  }

  @Test
  void aMomentWithoutZoneIsRefused() {
    assertRefused(batch(POINT.replace("-05:00", "")), "points[0].observed_at");
  }

  @Test
  void aBodyThatIsNotTheProtocolShapeIsRefused() {
    assertRefused("{\"points\":", "body");
    assertRefused("[]", "body");
    assertRefused("{}", "points");
    assertRefused("{\"points\":{}}", "points");
  }

  private void assertRefused(String text, String field) {
    assertThatThrownBy(() -> adapter.parse(body(text)))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            error -> assertThat(error.details().toString()).contains(field));
  }
}
