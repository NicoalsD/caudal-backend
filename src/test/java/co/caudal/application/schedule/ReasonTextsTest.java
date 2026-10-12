package co.caudal.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.schedule.SectorCandidate;
import co.caudal.domain.schedule.TurnExplainer;
import co.caudal.domain.schedule.TurnReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReasonTextsTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

  @Test
  void theExampleOfTheRulesReadsAsWritten() {
    TurnExplainer explainer =
        new TurnExplainer(
            new TurnExplainer.DayFacts(
                new BigDecimal("2.10"),
                "LOW",
                new BigDecimal("8.00"),
                new BigDecimal("1.80"),
                new BigDecimal("2.10"),
                new BigDecimal("2.40"),
                TODAY,
                new BigDecimal("1.00"),
                false));
    SectorCandidate school =
        new SectorCandidate(
            UUID.randomUUID(), "ESC", "Escuela", true, true, 1, new BigDecimal("10"), "ESTIMATED");
    SectorCandidate south =
        new SectorCandidate(
            UUID.randomUUID(),
            "SUR",
            "Sector Sur",
            true,
            false,
            null,
            new BigDecimal("22.0"),
            "CONFIRMED");

    List<String> first =
        explainer.explain(school).stream()
            .map(r -> ReasonTexts.text(r.code(), r.params(), TODAY))
            .toList();
    List<TurnReason> second = explainer.explain(south);

    assertThat(first)
        .containsExactly(
            "Escuela es prioritario y recibe turno antes que los demás.",
            "Escuela lleva 10 horas sin servicio (dato estimado, según el último turno publicado).",
            "Nivel del tanque 2,10 (banda LOW): hay 8 horas de servicio hoy.",
            "Para hoy se espera un nivel de 2,10 (entre 1,80 y 2,40).",
            "El nivel posible más bajo (1,80) está por encima de la reserva (1,00); no se recortan"
                + " horas.");
    assertThat(second)
        .extracting(TurnReason::code)
        .containsExactly("HOURS_WITHOUT_SERVICE", "TANK_BAND");
    assertThat(ReasonTexts.text(second.getFirst().code(), second.getFirst().params(), TODAY))
        .isEqualTo("Sector Sur lleva 22 horas sin servicio.");
  }

  @Test
  void oneHourHalfHoursTomorrowACutAndTheBoard() {
    assertThat(
            ReasonTexts.text(
                "TANK_BAND", Map.of("nivel", "1.4", "banda", "CRITICAL", "horas", "1.00"), TODAY))
        .isEqualTo("Nivel del tanque 1,40 (banda CRITICAL): hay 1 hora de servicio hoy.");
    assertThat(
            ReasonTexts.text(
                "HOURS_WITHOUT_SERVICE", Map.of("sector", "Alto", "horas", "2.5"), TODAY))
        .isEqualTo("Alto lleva 2,5 horas sin servicio.");
    assertThat(
            ReasonTexts.text(
                "FORECAST_RANGE",
                Map.of("p10", "1", "p50", "2", "p90", "3", "dia", "2026-10-10"),
                TODAY))
        .startsWith("Para mañana");
    assertThat(
            ReasonTexts.text(
                "RESERVE_GUARD",
                Map.of("p10", "0.9", "reserve_level", "1", "recortado", "true"),
                TODAY))
        .contains("se recortan las horas");
    assertThat(ReasonTexts.text("BOARD_CHANGE", Map.of("motivo", "Clase especial."), TODAY))
        .isEqualTo("La Junta cambió este turno: Clase especial.");
    assertThat(ReasonTexts.text("UNKNOWN", Map.of(), TODAY)).isEqualTo("UNKNOWN");
  }
}
