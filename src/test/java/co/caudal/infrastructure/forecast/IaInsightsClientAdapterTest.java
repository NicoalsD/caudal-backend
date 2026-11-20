package co.caudal.infrastructure.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsCallResult;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The client of the insights contract against a fake service (P06). */
class IaInsightsClientAdapterTest {

  private static final UUID VALVE = UUID.randomUUID();
  private static final String TOKEN = "service-token-for-tests";
  private HttpServer server;
  private long delayMs;
  private final AtomicReference<String> received = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> path = new AtomicReference<>();

  private static final InsightsSnapshot SNAPSHOT =
      new InsightsSnapshot(
          UUID.randomUUID(),
          "Vereda de Prueba",
          ZoneId.of("America/Bogota"),
          Instant.parse("2026-11-07T15:00:00Z"),
          "ASSISTED",
          new BigDecimal("25.00"),
          null,
          new InsightsSnapshot.Tank(
              UUID.randomUUID(),
              "Tanque",
              new BigDecimal("1.8"),
              new BigDecimal("2.5"),
              new BigDecimal("72.0"),
              "HIGH",
              Instant.parse("2026-11-07T14:55:00Z"),
              "STABLE"),
          List.of(
              new InsightsSnapshot.ForecastPoint(
                  Instant.parse("2026-11-08T05:00:00Z"),
                  new BigDecimal("1.6"),
                  new BigDecimal("1.8"),
                  new BigDecimal("2.0"))),
          List.of(
              new InsightsSnapshot.Valve(VALVE, UUID.randomUUID(), "Alto", null, "OPEN", true, 1)),
          List.of(
              new InsightsSnapshot.Shift(
                  UUID.randomUUID(),
                  Instant.parse("2026-11-07T16:00:00Z"),
                  Instant.parse("2026-11-07T17:00:00Z"),
                  "PLANNED")),
          List.of(new InsightsSnapshot.Anomaly("STALE_DATA", "MEDIUM")));

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private IaInsightsClientAdapter adapter(String url, int status, String body) throws IOException {
    if (url == null) {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            try {
              Thread.sleep(delayMs);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
            received.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(bytes);
            } catch (IOException closed) {
              // The client gave up after its timeout.
            }
          });
      server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
      server.start();
      url = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    IaProperties properties = new IaProperties(url, TOKEN, 300, 300);
    return new IaInsightsClientAdapter(
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
        properties,
        new InsightsProperties(300),
        JsonMapper.builder().build());
  }

  private static String recommendations(String items) {
    return "{\"source\":\"LLM\",\"model\":\"gemini-test\",\"generated_at\":\"2026-11-07T10:00:00-05:00\","
        + "\"summary\":\"Todo bien.\",\"recommendations\":["
        + items
        + "]}";
  }

  @Test
  void theSnapshotGoesOutWithTheFieldsOfTheContractAndTheTokenOfTheService() throws IOException {
    IaInsightsClientAdapter adapter =
        adapter(
            null,
            200,
            recommendations(
                "{\"action\":\"CLOSE\",\"valve_id\":\""
                    + VALVE
                    + "\",\"reason\":\"Cerrar.\",\"confidence\":0.8},"
                    + "{\"action\":\"WAIT\",\"valve_id\":null,\"reason\":\"Esperar.\",\"confidence\":0.4}"));

    InsightsCallResult<InsightsRecommendations> result = adapter.recommend(SNAPSHOT);

    assertThat(path.get()).isEqualTo("/v1/insights/recommendations");
    assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
    assertThat(received.get())
        .contains(
            "\"aqueduct_name\":\"Vereda de Prueba\"",
            "\"generated_at\":\"2026-11-07T10:00:00-05:00\"",
            "\"reserve_floor_pct\":25.00",
            "\"level_pct\":72.0",
            "\"is_motorized\":true",
            "\"region_name\":null",
            "\"starts_at\":\"2026-11-07T11:00:00-05:00\"",
            "\"severity\":\"MEDIUM\"")
        .doesNotContain("aqueductId", "levelPct");
    InsightsCallResult.Success<InsightsRecommendations> success =
        (InsightsCallResult.Success<InsightsRecommendations>) result;
    assertThat(success.value().source()).isEqualTo("LLM");
    assertThat(success.value().model()).isEqualTo("gemini-test");
    assertThat(success.value().recommendations()).hasSize(2);
    assertThat(success.value().recommendations().getFirst().valveId()).isEqualTo(VALVE);
    assertThat(success.value().recommendations().get(1).valveId()).isNull();
  }

  @Test
  void anAnswerAndAReportAreParsed() throws IOException {
    IaInsightsClientAdapter ask =
        adapter(
            null,
            200,
            "{\"source\":\"RULES\",\"model\":\"rules-v1\",\"generated_at\":\"2026-11-07T10:00:00-05:00\","
                + "\"answer\":\"Sí alcanza.\"}");
    InsightsCallResult<InsightsAnswer> answer = ask.ask("¿Alcanza?", SNAPSHOT);
    assertThat(((InsightsCallResult.Success<InsightsAnswer>) answer).value().answer())
        .isEqualTo("Sí alcanza.");
    assertThat(path.get()).isEqualTo("/v1/insights/ask");
    assertThat(received.get()).contains("\"question\":\"¿Alcanza?\"", "\"snapshot\"");
    stop();

    IaInsightsClientAdapter report =
        adapter(
            null,
            200,
            "{\"source\":\"LLM\",\"model\":\"m\",\"generated_at\":\"2026-11-07T10:00:00-05:00\","
                + "\"title\":\"T\",\"summary\":\"S\",\"findings\":[\"a\"],\"recommendations\":[\"b\"],"
                + "\"markdown\":\"# T\"}");
    InsightsCallResult<InsightsReport> narrative =
        report.report(
            new ReportRequest(
                "Vereda",
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-08T00:00:00Z"),
                Map.of("levels", Map.of("readings", 3))));
    assertThat(((InsightsCallResult.Success<InsightsReport>) narrative).value().findings())
        .containsExactly("a");
    assertThat(path.get()).isEqualTo("/v1/insights/report");
    assertThat(received.get()).contains("\"aggregates\":{\"levels\":{\"readings\":3}}");
  }

  @Test
  void serverErrorsAndInvalidAnswersCountForTheCircuit() throws IOException {
    assertCounts(adapter(null, 503, "{}").recommend(SNAPSHOT), true);
    stop();
    assertCounts(adapter(null, 401, "{}").recommend(SNAPSHOT), true);
    stop();
    assertCounts(adapter(null, 429, "{}").recommend(SNAPSHOT), true);
    stop();
    assertCounts(adapter(null, 200, "not json").recommend(SNAPSHOT), true);
    stop();
    assertCounts(
        adapter(null, 200, recommendations("{\"action\":\"WAIT\",\"reason\":\"x\"}"))
            .recommend(SNAPSHOT),
        true);
    stop();
    assertCounts(
        adapter(
                null,
                200,
                "{\"source\":\"GPT\",\"model\":\"m\",\"generated_at\":\"2026-11-07T10:00:00Z\",\"recommendations\":[]}")
            .recommend(SNAPSHOT),
        true);
  }

  @Test
  void aRefusalAboutTheRequestDoesNotCountForTheCircuit() throws IOException {
    assertCounts(adapter(null, 422, "{}").recommend(SNAPSHOT), false);
    stop();
    assertCounts(adapter(null, 400, "{}").ask("¿?", SNAPSHOT), false);
  }

  @Test
  void aSlowServiceTimesOutAndCounts() throws IOException {
    delayMs = 1_000;
    assertCounts(adapter(null, 200, recommendations("")).recommend(SNAPSHOT), true);
  }

  @Test
  void aServiceThatIsNotConfiguredFailsWithoutCallingAndWithoutCounting() throws IOException {
    IaProperties none = new IaProperties("", "", 300, 300);
    IaInsightsClientAdapter adapter =
        new IaInsightsClientAdapter(
            HttpClient.newHttpClient(),
            none,
            new InsightsProperties(300),
            JsonMapper.builder().build());

    assertCounts(adapter.recommend(SNAPSHOT), false);
  }

  @Test
  void aReportWithTooManyAggregatesIsNotSent() throws IOException {
    IaInsightsClientAdapter adapter = adapter(null, 200, "{}");
    Map<String, Object> huge = Map.of("blob", "x".repeat(40_000));

    InsightsCallResult<InsightsReport> result =
        adapter.report(new ReportRequest("V", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), huge));

    assertCounts(result, false);
    assertThat(received.get()).isNull();
  }

  private static void assertCounts(InsightsCallResult<?> result, boolean counts) {
    assertThat(result).isInstanceOf(InsightsCallResult.Failure.class);
    assertThat(((InsightsCallResult.Failure<?>) result).countsForCircuit()).isEqualTo(counts);
  }
}
