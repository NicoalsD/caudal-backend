package co.caudal.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.schedule.GetDayScheduleUseCase;
import co.caudal.application.schedule.ItemView;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.application.schedule.PublishedDay;
import co.caudal.application.schedule.ReasonView;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.WebSecurityTestConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = DayScheduleController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class DayScheduleControllerWebTest {

  private static final String DAY = "/api/v1/days/2026-10-16/schedule";
  private static final UUID OPERATOR = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final UUID PROPOSAL = UUID.fromString("0190f3a2-0000-7000-8000-0000000000b1");
  private static final UUID SHIFT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000c1");
  private static final UUID SECTOR = UUID.fromString("0190f3a2-0000-7000-8000-0000000000d1");

  @Autowired private MockMvc mvc;
  @MockitoBean private GetDayScheduleUseCase schedule;

  private static RequestPostProcessor withAuthority(String authority, String role) {
    return jwt()
        .jwt(
            j ->
                j.subject(OPERATOR.toString())
                    .claim("role", role)
                    .claim("aqueduct_id", AQUEDUCT.toString()))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static RequestPostProcessor operator() {
    return withAuthority("SCHEDULE_VIEW", "OPERATOR");
  }

  private static PublishedDay published() {
    ProposalSummary summary =
        new ProposalSummary(
            PROPOSAL,
            LocalDate.of(2026, 10, 16),
            "PUBLISHED",
            UUID.randomUUID(),
            new BigDecimal("2.10"),
            "LOW",
            new BigDecimal("8.00"),
            null,
            "PRIORITY_THEN_LONGEST_WAIT",
            Instant.parse("2026-10-15T20:00:00Z"),
            2);
    ItemView item =
        new ItemView(
            SHIFT,
            SECTOR,
            1,
            Instant.parse("2026-10-16T13:00:00Z"),
            Instant.parse("2026-10-16T16:00:00Z"),
            "PROPOSED",
            List.of(
                new ReasonView(
                    "PRIORITY_SECTOR",
                    Map.of("sector", "La Esperanza"),
                    "Sector prioritario por nivel bajo.")));
    return new PublishedDay(
        new ProposalDetail(summary, List.of(item), List.of(), List.of(), false),
        Instant.parse("2026-10-15T20:05:00Z"));
  }

  @Test
  void anOperatorReadsThePublishedShiftsWithTheExecutionIds() throws Exception {
    when(schedule.execute(any(), any())).thenReturn(published());

    mvc.perform(get(DAY).with(operator()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.service_date").value("2026-10-16"))
        .andExpect(jsonPath("$.proposal_id").value(PROPOSAL.toString()))
        .andExpect(jsonPath("$.published_at").value("2026-10-15T20:05:00Z"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(SHIFT.toString()))
        .andExpect(jsonPath("$.items[0].sector_id").value(SECTOR.toString()))
        .andExpect(jsonPath("$.items[0].sequence").value(1))
        .andExpect(jsonPath("$.items[0].start_at").value("2026-10-16T13:00:00Z"))
        .andExpect(jsonPath("$.items[0].end_at").value("2026-10-16T16:00:00Z"))
        .andExpect(jsonPath("$.items[0].origin").value("PROPOSED"))
        .andExpect(jsonPath("$.items[0].reasons[0].code").value("PRIORITY_SECTOR"))
        .andExpect(
            jsonPath("$.items[0].reasons[0].text").value("Sector prioritario por nivel bajo."))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.lock_version").doesNotExist());
  }

  @Test
  void aUserWithoutScheduleViewAnswers403AndNothingIsRead() throws Exception {
    mvc.perform(get(DAY).with(withAuthority("DAY_CLOSE", "OPERATOR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(schedule);
  }

  @Test
  void aBadDateAnswers400BeforeReadingAnything() throws Exception {
    mvc.perform(get("/api/v1/days/16-10-2026/schedule").with(operator()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(schedule);
  }

  @Test
  void aDayWithoutPublishedScheduleAnswers404() throws Exception {
    when(schedule.execute(any(), any())).thenThrow(new DomainException(ErrorCode.NOT_FOUND));

    mvc.perform(get(DAY).with(operator()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
