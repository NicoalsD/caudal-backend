package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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

/**
 * The assistant against a fake caudal-ia (docs/Contrato-IA.md, insights section): the answers of
 * the model are used and checked again, the guard of the reserve applies to them too, and a failing
 * service is replaced by the rules until its circuit breaker closes again.
 */
// The breaker lives in memory for the whole context: the healthy cases run before it opens.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InsightsServiceIT extends SessionIT {

  private static final String TOKEN = "test-service-token";
  private static final AtomicReference<String> MODE = new AtomicReference<>("ok");
  private static final AtomicReference<String> VALVE = new AtomicReference<>("");
  private static final AtomicReference<String> ACTION = new AtomicReference<>("CLOSE");
  private static final AtomicReference<String> LAST_BODY = new AtomicReference<>("");
  private static final AtomicReference<String> LAST_AUTH = new AtomicReference<>("");
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final HttpServer IA = start();

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/v1/insights/recommendations", InsightsServiceIT::recommendations);
      server.createContext(
          "/v1/insights/ask",
          exchange ->
              answer(
                  exchange,
                  "{\"source\":\"LLM\",\"model\":\"modelo-de-prueba\","
                      + "\"generated_at\":\"2026-11-07T10:00:00-05:00\","
                      + "\"answer\":\"Sí alcanza, el tanque está alto.\"}"));
      server.createContext(
          "/v1/insights/report",
          exchange ->
              answer(
                  exchange,
                  "{\"source\":\"LLM\",\"model\":\"modelo-de-prueba\","
                      + "\"generated_at\":\"2026-11-07T10:00:00-05:00\","
                      + "\"title\":\"Reporte semanal\",\"summary\":\"Semana tranquila.\","
                      + "\"findings\":[\"Sin fugas\"],\"recommendations\":[\"Seguir igual\"],"
                      + "\"markdown\":\"# Reporte semanal\"}"));
      server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void recommendations(HttpExchange exchange) throws IOException {
    String valve = VALVE.get();
    String items =
        "{\"action\":\""
            + ACTION.get()
            + "\",\"valve_id\":\""
            + valve
            + "\",\"reason\":\"Motivo del modelo.\",\"confidence\":1.7},"
            + "{\"action\":\"OPEN\",\"valve_id\":\"00000000-0000-4000-8000-0000000000aa\","
            + "\"reason\":\"Llave que no existe.\",\"confidence\":0.9},"
            + "{\"action\":\"WAIT\",\"valve_id\":null,\"reason\":\"Esperar.\",\"confidence\":0.5},"
            + "{\"action\":\"INSPECT\",\"valve_id\":null,\"reason\":\"Revisar.\",\"confidence\":0.5},"
            + "{\"action\":\"WAIT\",\"valve_id\":null,\"reason\":\"Esperar 2.\",\"confidence\":0.5},"
            + "{\"action\":\"WAIT\",\"valve_id\":null,\"reason\":\"Esperar 3.\",\"confidence\":0.5},"
            + "{\"action\":\"WAIT\",\"valve_id\":null,\"reason\":\"Esperar 4.\",\"confidence\":0.5}";
    answer(
        exchange,
        "{\"source\":\"LLM\",\"model\":\"modelo-de-prueba\","
            + "\"generated_at\":\"2026-11-07T10:00:00-05:00\",\"summary\":\"Resumen.\","
            + "\"recommendations\":["
            + items
            + "]}");
  }

  private static void answer(HttpExchange exchange, String json) throws IOException {
    CALLS.incrementAndGet();
    LAST_AUTH.set(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
    LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    int status = "down".equals(MODE.get()) ? 503 : 200;
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  @DynamicPropertySource
  static void iaProperties(DynamicPropertyRegistry registry) {
    registry.add("caudal.ia.service-url", () -> "http://127.0.0.1:" + IA.getAddress().getPort());
    registry.add("caudal.ia.service-token", () -> TOKEN);
    registry.add("caudal.insights.read-timeout-ms", () -> "2000");
    registry.add("caudal.ia.circuit-breaker.failure-threshold", () -> "2");
  }

  @AfterAll
  static void stop() {
    IA.stop(0);
  }

  private record Site(UUID aqueduct, Session admin, UUID valve) {}

  private Site site(String name, String level) throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account(name, "BOARD_ADMIN", aqueduct));
    UUID tank =
        owner.queryForObject(
            "SELECT id FROM org.tanks WHERE aqueduct_id = ?", UUID.class, aqueduct);
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    Timestamp now = Timestamp.from(Instant.now());
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?,"
            + " 'ACCEPTED')",
        aqueduct,
        tank,
        level,
        now,
        now,
        ruleSet);
    UUID valve =
        owner.queryForObject(
            "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
                + " WHERE s.aqueduct_id = ? ORDER BY v.code LIMIT 1",
            UUID.class,
            aqueduct);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/valves/" + valve)
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"is_motorized\":true}"))
        .andExpect(status().isOk());
    return new Site(aqueduct, admin, valve);
  }

  private ResultActions run(Session session) throws Exception {
    return mvc.perform(post("/api/v1/autopilot/run").header("Authorization", session.bearer()));
  }

  private ResultActions ask(Session session) throws Exception {
    return mvc.perform(
        post("/api/v1/assistant/ask")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"¿Alcanza el agua para mañana?\"}"));
  }

  private ResultActions narrative(Session session) throws Exception {
    Instant now = Instant.now();
    return mvc.perform(
        post("/api/v1/reports/narrative")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"from\":\"" + now.minusSeconds(86_400) + "\",\"to\":\"" + now + "\"}"));
  }

  @Test
  @Order(1)
  void theModelAnswersAreCheckedAgainAndStoredAsLlmDecisions() throws Exception {
    MODE.set("ok");
    ACTION.set("CLOSE");
    Site on = site("insights.llm", "4.00");
    VALVE.set(on.valve().toString());

    run(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.items[0].source").value("LLM"))
        .andExpect(jsonPath("$.items[0].model").value("modelo-de-prueba"))
        .andExpect(jsonPath("$.items[0].action").value("CLOSE"))
        .andExpect(jsonPath("$.items[0].confidence").value(1.0))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + TOKEN);
    assertThat(LAST_BODY.get())
        .contains("\"snapshot\"", "\"reserve_floor_pct\"", "\"is_motorized\":true")
        .doesNotContain("password");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.autopilot_decisions WHERE aqueduct_id = ?"
                    + " AND valve_id = '00000000-0000-4000-8000-0000000000aa'",
                Integer.class,
                on.aqueduct()))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT inputs->>'mode' FROM ops.autopilot_decisions WHERE aqueduct_id = ?"
                    + " LIMIT 1",
                String.class,
                on.aqueduct()))
        .isEqualTo("ASSISTED");
  }

  @Test
  @Order(2)
  void anOpenFromTheModelAtTheReserveFloorIsSkipped() throws Exception {
    MODE.set("ok");
    ACTION.set("OPEN");
    Site on = site("insights.floor", "1.00");
    VALVE.set(on.valve().toString());

    run(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("OPEN"))
        .andExpect(jsonPath("$.items[0].status").value("SKIPPED"))
        .andExpect(jsonPath("$.items[0].status_detail").value("RESERVE_FLOOR"));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE aqueduct_id = ?",
                Integer.class,
                on.aqueduct()))
        .isZero();
  }

  @Test
  @Order(3)
  void theAssistantAndTheNarrativeUseTheModel() throws Exception {
    MODE.set("ok");
    Site on = site("insights.ask", "4.00");

    ask(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("LLM"))
        .andExpect(jsonPath("$.model").value("modelo-de-prueba"))
        .andExpect(jsonPath("$.answer").value("Sí alcanza, el tanque está alto."));
    assertThat(LAST_BODY.get()).contains("\"question\"", "\"snapshot\"");
    narrative(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("LLM"))
        .andExpect(jsonPath("$.title").value("Reporte semanal"))
        .andExpect(jsonPath("$.markdown").value("# Reporte semanal"));
    assertThat(LAST_BODY.get()).contains("\"aggregates\"", "\"aqueduct_name\"");
  }

  @Test
  @Order(4)
  void aFailingServiceIsReplacedByTheRulesAndTheCircuitOpens() throws Exception {
    Site on = site("insights.down", "4.00");
    MODE.set("down");

    ask(on.admin()).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("RULES"));
    narrative(on.admin()).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("RULES"));
    int callsBefore = CALLS.get();
    ask(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("RULES"))
        .andExpect(jsonPath("$.model").value("rules-v1"));
    run(on.admin()).andExpect(status().isOk());

    assertThat(CALLS.get()).as("the open circuit makes no more calls").isEqualTo(callsBefore);
  }
}
