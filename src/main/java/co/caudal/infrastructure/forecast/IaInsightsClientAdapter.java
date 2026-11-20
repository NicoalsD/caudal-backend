package co.caudal.infrastructure.forecast;

import co.caudal.application.autopilot.InsightRecommendation;
import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsCallResult;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;
import co.caudal.application.port.out.InsightsServicePort;
import co.caudal.shared.FieldLimits;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Speaks the insights contract of {@code caudal-ia} (docs/Contrato-IA.md, insights section) with
 * the same service URL and token as the forecast. It turns the snapshot into the JSON of the
 * contract (only the fields of the contract, because the service rejects unknown ones), checks the
 * answer again and maps each error to an {@link InsightsCallResult.Failure}. A refusal about the
 * request (4xx other than 401) proves the service is up and does not count for the circuit breaker;
 * timeouts, network errors, 5xx, 401, 429 and invalid answers do.
 *
 * @pattern P06 Adapter
 */
public class IaInsightsClientAdapter implements InsightsServicePort {

  private static final Logger LOG = LoggerFactory.getLogger(IaInsightsClientAdapter.class);
  private static final String RECOMMENDATIONS_PATH = "/v1/insights/recommendations";
  private static final String REPORT_PATH = "/v1/insights/report";
  private static final String ASK_PATH = "/v1/insights/ask";

  /** Always writes the seconds ({@code 10:00:00-05:00}), which every ISO 8601 parser accepts. */
  private static final DateTimeFormatter AT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private static final int OK = 200;
  private static final int UNAUTHORIZED = 401;
  private static final int TOO_MANY_REQUESTS = 429;
  private static final int CLIENT_ERROR = 400;
  private static final int SERVER_ERROR = 500;

  private final HttpClient http;
  private final IaProperties properties;
  private final InsightsProperties insights;
  private final JsonMapper json;

  /**
   * Creates the adapter.
   *
   * @param http the HTTP client
   * @param properties URL and token of the service
   * @param insights timeouts of the assistant
   * @param json JSON reader and writer
   */
  public IaInsightsClientAdapter(
      HttpClient http, IaProperties properties, InsightsProperties insights, JsonMapper json) {
    this.http = http;
    this.properties = properties;
    this.insights = insights;
    this.json = json;
  }

  @Override
  public InsightsCallResult<InsightsRecommendations> recommend(InsightsSnapshot snapshot) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("snapshot", snapshot(snapshot));
    return call(RECOMMENDATIONS_PATH, body, this::recommendations);
  }

  @Override
  public InsightsCallResult<InsightsReport> report(ReportRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("aqueduct_name", request.aqueductName());
    body.put("from", request.from().toString());
    body.put("to", request.to().toString());
    body.put("aggregates", request.aggregates());
    return call(REPORT_PATH, body, this::report);
  }

  @Override
  public InsightsCallResult<InsightsAnswer> ask(String question, InsightsSnapshot snapshot) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("question", question);
    body.put("snapshot", snapshot(snapshot));
    return call(ASK_PATH, body, this::answer);
  }

  private <T> InsightsCallResult<T> call(
      String path, Map<String, Object> body, Function<JsonNode, T> parser) {
    if (!properties.configured()) {
      return failure(false, "service not configured");
    }
    String payload = json.writeValueAsString(body);
    if (path.equals(REPORT_PATH)
        && json.writeValueAsString(body.get("aggregates")).getBytes(StandardCharsets.UTF_8).length
            > FieldLimits.REPORT_AGGREGATES_MAX_BYTES) {
      return failure(false, "aggregates too large");
    }
    HttpResponse<String> response;
    try {
      response = http.send(request(path, payload), HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException slow) {
      return failure(true, "timeout");
    } catch (IOException network) {
      return failure(true, network.getClass().getSimpleName());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return failure(true, "interrupted");
    }
    return interpret(response.statusCode(), response.body(), parser);
  }

  HttpRequest request(String path, String payload) {
    return HttpRequest.newBuilder(URI.create(properties.serviceUrl() + path))
        .header("Authorization", "Bearer " + properties.serviceToken())
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .timeout(insights.readTimeout())
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
  }

  <T> InsightsCallResult<T> interpret(int status, String body, Function<JsonNode, T> parser) {
    if (status == OK) {
      try {
        return new InsightsCallResult.Success<>(parser.apply(json.readTree(body)));
      } catch (RuntimeException malformed) {
        LOG.warn(
            "Insights service answered an invalid body: {}", malformed.getClass().getSimpleName());
        return failure(true, "invalid answer");
      }
    }
    boolean refusal =
        status >= CLIENT_ERROR
            && status < SERVER_ERROR
            && status != UNAUTHORIZED
            && status != TOO_MANY_REQUESTS;
    if (refusal) {
      LOG.warn("Insights service refused the request: status={}", status);
    }
    return failure(!refusal, "status " + status);
  }

  private static <T> InsightsCallResult<T> failure(boolean counts, String detail) {
    return new InsightsCallResult.Failure<>(counts, detail);
  }

  // --- request ---

  private static Map<String, Object> snapshot(InsightsSnapshot s) {
    ZoneId zone = s.zone();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("aqueduct_id", s.aqueductId().toString());
    map.put("aqueduct_name", s.aqueductName());
    map.put("generated_at", at(s.generatedAt(), zone));
    map.put("mode", s.mode());
    map.put("reserve_floor_pct", s.reserveFloorPct());
    map.put(
        "operator_last_action_at",
        s.operatorLastActionAt() == null ? null : at(s.operatorLastActionAt(), zone));
    Map<String, Object> tank = new LinkedHashMap<>();
    tank.put("id", s.tank().id().toString());
    tank.put("name", s.tank().name());
    tank.put("level_m", s.tank().levelM());
    tank.put("max_level_m", s.tank().maxLevelM());
    tank.put("level_pct", s.tank().levelPct());
    tank.put("band", s.tank().band());
    tank.put("observed_at", s.tank().observedAt() == null ? null : at(s.tank().observedAt(), zone));
    tank.put("trend", s.tank().trend());
    map.put("tank", tank);
    map.put("forecast", s.forecast().stream().map(p -> forecastPoint(p, zone)).toList());
    map.put("valves", s.valves().stream().map(IaInsightsClientAdapter::valve).toList());
    map.put("schedule", s.schedule().stream().map(shift -> shift(shift, zone)).toList());
    map.put("anomalies", s.anomalies().stream().map(IaInsightsClientAdapter::anomaly).toList());
    return map;
  }

  private static Map<String, Object> forecastPoint(InsightsSnapshot.ForecastPoint p, ZoneId zone) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("timestamp", at(p.timestamp(), zone));
    map.put("p10", p.p10());
    map.put("p50", p.p50());
    map.put("p90", p.p90());
    return map;
  }

  private static Map<String, Object> valve(InsightsSnapshot.Valve v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", v.id().toString());
    map.put("sector_id", v.sectorId().toString());
    map.put("sector_name", v.sectorName());
    map.put("region_name", v.regionName());
    map.put("state", v.state());
    map.put("is_motorized", v.motorized());
    map.put("priority", v.priority());
    return map;
  }

  private static Map<String, Object> shift(InsightsSnapshot.Shift shift, ZoneId zone) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("sector_id", shift.sectorId().toString());
    map.put("starts_at", at(shift.startsAt(), zone));
    map.put("ends_at", at(shift.endsAt(), zone));
    map.put("status", shift.status());
    return map;
  }

  private static Map<String, Object> anomaly(InsightsSnapshot.Anomaly a) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("kind", a.kind());
    map.put("severity", a.severity());
    return map;
  }

  private static String at(Instant instant, ZoneId zone) {
    return AT.format(instant.atZone(zone));
  }

  // --- answers ---

  private InsightsRecommendations recommendations(JsonNode root) {
    JsonNode items = root.path("recommendations");
    if (!items.isArray()) {
      throw new IllegalArgumentException("recommendations");
    }
    List<InsightRecommendation> list = new ArrayList<>();
    for (JsonNode node : items) {
      JsonNode valve = node.path("valve_id");
      list.add(
          new InsightRecommendation(
              node.path("action").asString(""),
              valve.isNull() || valve.isMissingNode() ? null : UUID.fromString(valve.asString()),
              node.path("reason").asString(""),
              confidence(node.path("confidence"))));
    }
    return new InsightsRecommendations(
        source(root), text(root, "model"), moment(root), root.path("summary").asString(""), list);
  }

  private InsightsReport report(JsonNode root) {
    return new InsightsReport(
        source(root),
        text(root, "model"),
        moment(root),
        text(root, "title"),
        root.path("summary").asString(""),
        strings(root.path("findings")),
        strings(root.path("recommendations")),
        text(root, "markdown"));
  }

  private InsightsAnswer answer(JsonNode root) {
    return new InsightsAnswer(
        source(root), text(root, "model"), moment(root), text(root, "answer"));
  }

  private static String source(JsonNode root) {
    String source = root.path("source").asString("");
    if (!InsightsRecommendations.SOURCE_LLM.equals(source)
        && !InsightsRecommendations.SOURCE_RULES.equals(source)) {
      throw new IllegalArgumentException("source");
    }
    return source;
  }

  private static String text(JsonNode root, String field) {
    String value = root.path(field).asString("");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field);
    }
    return value;
  }

  private static Instant moment(JsonNode root) {
    return OffsetDateTime.parse(text(root, "generated_at")).toInstant();
  }

  private static BigDecimal confidence(JsonNode node) {
    if (!node.isNumber() || !Double.isFinite(node.asDouble())) {
      throw new IllegalArgumentException("confidence");
    }
    return node.decimalValue();
  }

  private static List<String> strings(JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("list");
    }
    List<String> list = new ArrayList<>();
    for (JsonNode item : node) {
      list.add(item.asString(""));
    }
    return list;
  }
}
