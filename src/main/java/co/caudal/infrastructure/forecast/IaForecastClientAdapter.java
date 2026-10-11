package co.caudal.infrastructure.forecast;

import co.caudal.application.forecast.ForecastCallResult;
import co.caudal.application.forecast.ForecastFailure;
import co.caudal.application.forecast.ForecastPoint;
import co.caudal.application.forecast.ForecastSeries;
import co.caudal.application.forecast.ModelForecast;
import co.caudal.application.port.out.ForecastServicePort;
import co.caudal.application.reading.LevelPoint;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Speaks the contract of {@code caudal-ia} (docs/Contrato-IA.md, section 3): it turns a series of
 * effective levels into {@code POST /v1/forecasts}, checks every rule of the answer again and maps
 * each error to a {@link ForecastFailure} as section 3.4 says. It waits at most the connect and
 * read timeouts (2 s and 10 s by default, section 4.1) and never retries in the same request. A 422
 * about the data does not count for the circuit breaker; timeouts, network errors, 5xx, 401 and
 * invalid answers do.
 *
 * @pattern P06 Adapter
 */
public class IaForecastClientAdapter implements ForecastServicePort {

  private static final Logger LOG = LoggerFactory.getLogger(IaForecastClientAdapter.class);
  private static final String PATH = "/v1/forecasts";
  private static final List<Double> QUANTILES = List.of(0.1, 0.5, 0.9);
  private static final int SCALE = 2;
  private static final int OK = 200;
  private static final int UNPROCESSABLE = 422;
  private static final int CLIENT_ERROR = 400;
  private static final int SERVER_ERROR = 500;
  private static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";

  private final HttpClient http;
  private final IaProperties properties;
  private final JsonMapper json;

  /**
   * Creates the adapter.
   *
   * @param http the HTTP client
   * @param properties URL and token of the service
   * @param json JSON reader and writer
   */
  public IaForecastClientAdapter(HttpClient http, IaProperties properties, JsonMapper json) {
    this.http = http;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public ForecastCallResult forecast(ForecastSeries series) {
    if (!properties.configured()) {
      return failure(ForecastFailure.IA_ERROR, false, "service not configured");
    }
    long started = System.nanoTime();
    HttpResponse<String> response;
    try {
      response = http.send(request(series), HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException slow) {
      return failure(ForecastFailure.IA_TIMEOUT, true, slow.getClass().getSimpleName());
    } catch (IOException network) {
      return failure(ForecastFailure.IA_ERROR, true, network.getClass().getSimpleName());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return failure(ForecastFailure.IA_ERROR, true, "interrupted");
    }
    long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
    return interpret(series, response.statusCode(), response.body(), latencyMs);
  }

  HttpRequest request(ForecastSeries series) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("series_id", series.seriesId().toString());
    body.put(
        "timestamps",
        series.points().stream()
            .map(p -> p.observedAt().atZone(series.zone()).toOffsetDateTime().toString())
            .toList());
    body.put("values", series.points().stream().map(LevelPoint::value).toList());
    body.put("prediction_length", series.horizonDays());
    body.put("quantile_levels", QUANTILES);
    return HttpRequest.newBuilder(URI.create(properties.serviceUrl() + PATH))
        .header("Authorization", "Bearer " + properties.serviceToken())
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .timeout(properties.readTimeout())
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        .build();
  }

  ForecastCallResult interpret(ForecastSeries series, int status, String body, long latencyMs) {
    if (status == OK) {
      return parse(series, body, latencyMs);
    }
    if (status == UNPROCESSABLE && INSUFFICIENT_HISTORY.equals(errorCode(body))) {
      return failure(ForecastFailure.INSUFFICIENT_HISTORY, false, "422 " + INSUFFICIENT_HISTORY);
    }
    // A 400 or another 422 means the backend built the series wrong: it is fixed in code, so it
    // does not open the circuit, but it is logged as an alert.
    boolean dataProblem = status == UNPROCESSABLE || status == CLIENT_ERROR;
    if (dataProblem) {
      LOG.warn("Forecast service refused the series: status={}", status);
    }
    boolean serviceProblem = !dataProblem || status >= SERVER_ERROR;
    return failure(ForecastFailure.IA_ERROR, serviceProblem, "status " + status);
  }

  private ForecastCallResult parse(ForecastSeries series, String body, long latencyMs) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode model = root.path("model");
      String name = model.path("name").asString("");
      String version = model.path("version").asString("");
      JsonNode points = root.path("points");
      if (name.isEmpty()
          || version.isEmpty()
          || !"D".equals(root.path("frequency").asString(""))
          || !points.isArray()
          || points.size() != series.horizonDays()) {
        return invalid("shape");
      }
      LocalDate expected = firstDay(series);
      List<ForecastPoint> result = new ArrayList<>();
      for (JsonNode node : points) {
        ForecastPoint point = point(node, series, expected);
        if (point == null) {
          return invalid("point");
        }
        result.add(point);
        expected = expected.plusDays(1);
      }
      return new ForecastCallResult.Success(new ModelForecast(name, version, result, latencyMs));
    } catch (RuntimeException malformed) {
      return invalid(malformed.getClass().getSimpleName());
    }
  }

  /** One point of the answer, or null if it is not the expected day or its range is crossed. */
  private static ForecastPoint point(JsonNode node, ForecastSeries series, LocalDate expected) {
    LocalDate day =
        OffsetDateTime.parse(node.path("timestamp").asString())
            .atZoneSameInstant(series.zone())
            .toLocalDate();
    BigDecimal p10 = number(node.path("p10"));
    BigDecimal p50 = number(node.path("p50"));
    BigDecimal p90 = number(node.path("p90"));
    boolean ordered =
        p10 != null
            && p50 != null
            && p90 != null
            && p10.compareTo(p50) <= 0
            && p50.compareTo(p90) <= 0;
    return day.equals(expected) && ordered ? new ForecastPoint(day, p10, p50, p90) : null;
  }

  private static LocalDate firstDay(ForecastSeries series) {
    LevelPoint last = series.points().getLast();
    return last.observedAt().atZone(series.zone()).toLocalDate().plusDays(1);
  }

  private static BigDecimal number(JsonNode node) {
    if (!node.isNumber() || !Double.isFinite(node.asDouble())) {
      return null;
    }
    return node.decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
  }

  private String errorCode(String body) {
    try {
      return json.readTree(body).path("error").path("code").asString("");
    } catch (JacksonException notJson) {
      return "";
    }
  }

  private static ForecastCallResult invalid(String detail) {
    LOG.warn("Forecast service answered an invalid forecast");
    return failure(ForecastFailure.IA_ERROR, true, "invalid answer: " + detail);
  }

  private static ForecastCallResult failure(String reason, boolean counts, String detail) {
    return new ForecastCallResult.Failure(new ForecastFailure(reason, counts, detail));
  }
}
