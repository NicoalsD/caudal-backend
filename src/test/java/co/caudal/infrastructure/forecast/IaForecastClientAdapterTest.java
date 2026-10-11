package co.caudal.infrastructure.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.forecast.ForecastCallResult;
import co.caudal.application.forecast.ForecastFailure;
import co.caudal.application.forecast.ForecastSeries;
import co.caudal.application.reading.LevelPoint;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IaForecastClientAdapterTest {

  static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  static final Instant LAST = Instant.parse("2026-10-08T23:00:00Z");

  private HttpServer server;
  private final AtomicReference<String> received = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();

  static ForecastSeries series(int horizon) {
    return new ForecastSeries(
        UUID.randomUUID(),
        List.of(
            new LevelPoint(new BigDecimal("2.35"), LAST.minusSeconds(86_400)),
            new LevelPoint(new BigDecimal("2.10"), LAST)),
        horizon,
        BOGOTA);
  }

  IaForecastClientAdapter adapter(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/forecasts",
        exchange -> {
          received.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    return new IaForecastClientAdapter(
        HttpClient.newHttpClient(),
        new IaProperties("http://127.0.0.1:" + server.getAddress().getPort(), "service-token"),
        JsonMapper.builder().build());
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  static String answer(String firstDay, String p10, String p50, String p90) {
    return "{\"model\":{\"name\":\"autogluon/chronos-2-small\",\"version\":\"2.0.0\"},"
        + "\"generated_at\":\"2026-10-09T09:30:00Z\",\"frequency\":\"D\",\"points\":["
        + "{\"timestamp\":\""
        + firstDay
        + "T00:00:00-05:00\",\"p10\":"
        + p10
        + ",\"p50\":"
        + p50
        + ",\"p90\":"
        + p90
        + "}]}";
  }

  @Test
  void sendsTheContractAndReadsAValidForecast() throws IOException {
    ForecastCallResult result =
        adapter(200, answer("2026-10-09", "1.80", "2.10", "2.40")).forecast(series(1));

    assertThat(result).isInstanceOf(ForecastCallResult.Success.class);
    ForecastCallResult.Success success = (ForecastCallResult.Success) result;
    assertThat(success.forecast().modelName()).isEqualTo("autogluon/chronos-2-small");
    assertThat(success.forecast().points().getFirst().targetDate())
        .isEqualTo(LocalDate.of(2026, 10, 9));
    assertThat(success.forecast().points().getFirst().p50()).isEqualByComparingTo("2.10");
    assertThat(authorization.get()).isEqualTo("Bearer service-token");
    assertThat(received.get())
        .contains("\"prediction_length\":1")
        .contains("\"quantile_levels\":[0.1,0.5,0.9]")
        .contains("2026-10-08T18:00-05:00")
        .contains("2.10");
  }

  @Test
  void anAnswerThatBreaksTheRulesIsAnErrorThatCounts() throws IOException {
    ForecastCallResult crossed =
        adapter(200, answer("2026-10-09", "2.50", "2.10", "2.40")).forecast(series(1));
    stop();
    ForecastCallResult wrongDay =
        adapter(200, answer("2026-10-11", "1.80", "2.10", "2.40")).forecast(series(1));
    stop();
    ForecastCallResult wrongLength =
        adapter(200, answer("2026-10-09", "1.80", "2.10", "2.40")).forecast(series(2));
    stop();
    ForecastCallResult notJson = adapter(200, "<html>").forecast(series(1));

    for (ForecastCallResult result : List.of(crossed, wrongDay, wrongLength, notJson)) {
      assertThat(((ForecastCallResult.Failure) result).failure())
          .extracting(ForecastFailure::reason, ForecastFailure::countsForCircuit)
          .containsExactly("IA_ERROR", true);
    }
  }

  @Test
  void eachErrorOfTheServiceMapsAsTheContractSays() throws IOException {
    ForecastFailure insufficient =
        ((ForecastCallResult.Failure)
                adapter(422, "{\"error\":{\"code\":\"INSUFFICIENT_HISTORY\"}}").forecast(series(1)))
            .failure();
    stop();
    ForecastFailure invalidSeries =
        ((ForecastCallResult.Failure)
                adapter(422, "{\"error\":{\"code\":\"INVALID_SERIES\"}}").forecast(series(1)))
            .failure();
    stop();
    ForecastFailure unauthorized =
        ((ForecastCallResult.Failure) adapter(401, "{}").forecast(series(1))).failure();
    stop();
    ForecastFailure notReady =
        ((ForecastCallResult.Failure) adapter(503, "not json").forecast(series(1))).failure();

    assertThat(insufficient.reason()).isEqualTo("INSUFFICIENT_HISTORY");
    assertThat(insufficient.countsForCircuit()).isFalse();
    assertThat(invalidSeries.reason()).isEqualTo("IA_ERROR");
    assertThat(invalidSeries.countsForCircuit()).isFalse();
    assertThat(unauthorized.countsForCircuit()).isTrue();
    assertThat(notReady.reason()).isEqualTo("IA_ERROR");
    assertThat(notReady.countsForCircuit()).isTrue();
  }

  @Test
  void withoutServiceOrWithANetworkErrorItFails() {
    IaForecastClientAdapter none =
        new IaForecastClientAdapter(
            HttpClient.newHttpClient(), new IaProperties(null, null), JsonMapper.builder().build());
    IaForecastClientAdapter refused =
        new IaForecastClientAdapter(
            HttpClient.newHttpClient(),
            new IaProperties("http://127.0.0.1:1", "token"),
            JsonMapper.builder().build());

    assertThat(((ForecastCallResult.Failure) none.forecast(series(1))).failure().countsForCircuit())
        .isFalse();
    assertThat(((ForecastCallResult.Failure) refused.forecast(series(1))).failure())
        .extracting(ForecastFailure::reason, ForecastFailure::countsForCircuit)
        .containsExactly("IA_ERROR", true);
    assertThat(new IaProperties("u", "secret").toString()).doesNotContain("secret");
  }
}
