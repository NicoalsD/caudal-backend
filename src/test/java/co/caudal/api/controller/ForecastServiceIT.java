package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** The forecast against a fake caudal-ia: the AI run, its shadow, a timeout and the breaker. */
// The breaker lives in memory for the whole context: the healthy case runs before it opens.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForecastServiceIT extends SessionIT {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final AtomicReference<String> MODE = new AtomicReference<>("ok");
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final HttpServer IA = start();

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/forecasts",
          exchange -> {
            CALLS.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if ("slow".equals(MODE.get())) {
              try {
                Thread.sleep(1500);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
            }
            String day = LocalDate.now(BOGOTA).toString();
            byte[] body =
                ("{\"model\":{\"name\":\"autogluon/chronos-2-small\",\"version\":\"2.0.0\"},"
                        + "\"generated_at\":\"2026-10-09T09:30:00Z\",\"frequency\":\"D\","
                        + "\"points\":[{\"timestamp\":\""
                        + day
                        + "T00:00:00-05:00\",\"p10\":2.00,\"p50\":2.40,\"p90\":2.70}]}")
                    .getBytes(StandardCharsets.UTF_8);
            int status = "down".equals(MODE.get()) ? 503 : 200;
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            } catch (IOException closed) {
              // The client gave up after its timeout.
            }
          });
      // A slow answer must not hold the next request, so each one gets its own thread.
      server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void iaProperties(DynamicPropertyRegistry registry) {
    registry.add("caudal.ia.service-url", () -> "http://127.0.0.1:" + IA.getAddress().getPort());
    registry.add("caudal.ia.service-token", () -> "test-service-token");
    registry.add("caudal.ia.read-timeout-ms", () -> "500");
    registry.add("caudal.ia.circuit-breaker.failure-threshold", () -> "2");
  }

  @AfterAll
  static void stop() {
    IA.stop(0);
  }

  private void history(UUID aqueduct, UUID tank) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    Instant now = Instant.now();
    String[] values = {"3.00", "2.90", "2.80", "2.70", "2.60"};
    for (int i = 0; i < values.length; i++) {
      Instant at = now.minus(values.length - i, ChronoUnit.DAYS);
      owner.update(
          "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
              + " observed_at, received_at, source, rule_set_id, validation_status) VALUES"
              + " (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?, 'ACCEPTED')",
          aqueduct,
          tank,
          values[i],
          java.sql.Timestamp.from(at),
          java.sql.Timestamp.from(at),
          ruleSet);
    }
  }

  private ResultActions forecast(Session session, UUID tank) throws Exception {
    return mvc.perform(
        post("/api/v1/tanks/" + tank + "/forecasts")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"horizon_days\":1}"));
  }

  @Test
  @Order(1)
  void theAiForecastIsShownAndTheSimpleEstimateIsKeptInTheShadow() throws Exception {
    MODE.set("ok");
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    history(aqueduct, tank);
    Session session = login(account("ia.ok", "BOARD_MEMBER", aqueduct));

    forecast(session, tank)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.model_name").value("autogluon/chronos-2-small"))
        .andExpect(jsonPath("$.is_fallback").value(false))
        .andExpect(jsonPath("$.points[0].p50").value(2.40));

    List<Map<String, Object>> runs =
        owner.queryForList(
            "SELECT model_name, is_fallback, fallback_reason FROM ops.forecast_runs"
                + " WHERE tank_id = ? ORDER BY model_name",
            tank);
    assertThat(runs).hasSize(2);
    assertThat(runs.get(1))
        .containsEntry("model_name", "naive-persistence")
        .containsEntry("is_fallback", false)
        .containsEntry("fallback_reason", "SHADOW_BASELINE");
  }

  @Test
  @Order(2)
  void aSlowServiceIsReplacedAndRepeatedFailuresOpenTheCircuit() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    history(aqueduct, tank);
    Session session = login(account("ia.slow", "BOARD_MEMBER", aqueduct));

    MODE.set("slow");
    forecast(session, tank)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.is_fallback").value(true))
        .andExpect(jsonPath("$.fallback_reason").value("IA_TIMEOUT"));
    MODE.set("down");
    forecast(session, tank).andExpect(jsonPath("$.fallback_reason").value("IA_ERROR"));
    int callsBefore = CALLS.get();
    forecast(session, tank)
        .andExpect(jsonPath("$.fallback_reason").value("CIRCUIT_OPEN"))
        .andExpect(jsonPath("$.model_name").value("naive-persistence"));
    assertThat(CALLS.get()).isEqualTo(callsBefore);
  }
}
