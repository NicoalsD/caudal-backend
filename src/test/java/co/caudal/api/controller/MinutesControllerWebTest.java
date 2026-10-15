package co.caudal.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.auth.Caller;
import co.caudal.application.command.CommandBus;
import co.caudal.application.minutes.GetMinutesPdfUseCase;
import co.caudal.application.minutes.GetMinutesUseCase;
import co.caudal.application.minutes.ListMinutesUseCase;
import co.caudal.application.minutes.MinutesSummaryView;
import co.caudal.application.pagination.Page;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.WebSecurityTestConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = MinutesController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class MinutesControllerWebTest {

  private static final String MINUTES = "/api/v1/minutes";
  private static final UUID MEMBER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final UUID MINUTES_ID = UUID.fromString("0190f3a2-0000-7000-8000-000000000010");

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;
  @MockitoBean private GetMinutesUseCase get;
  @MockitoBean private GetMinutesPdfUseCase pdf;
  @MockitoBean private ListMinutesUseCase list;

  private static RequestPostProcessor member(String authority) {
    return jwt()
        .jwt(
            j ->
                j.subject(MEMBER.toString())
                    .claim("role", "BOARD_MEMBER")
                    .claim("aqueduct_id", AQUEDUCT.toString()))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static MinutesSummaryView draft() {
    return new MinutesSummaryView(
        MINUTES_ID,
        LocalDate.parse("2026-10-01"),
        LocalDate.parse("2026-10-31"),
        "DRAFT",
        Instant.parse("2026-10-14T13:00:00Z"),
        null,
        null);
  }

  @Test
  void listsTheMinutesWithSnakeCaseFieldsWithoutSections() throws Exception {
    when(list.execute(any(Caller.class), eq(20), eq(null)))
        .thenReturn(new Page<>(List.of(draft()), "cursor-siguiente"));

    mvc.perform(get(MINUTES).with(member("MINUTES_VIEW")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items[0].id").value(MINUTES_ID.toString()))
        .andExpect(jsonPath("$.items[0].period_from").value("2026-10-01"))
        .andExpect(jsonPath("$.items[0].period_to").value("2026-10-31"))
        .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
        .andExpect(jsonPath("$.items[0].generated_at").value("2026-10-14T13:00:00Z"))
        .andExpect(jsonPath("$.items[0].finalized_at").doesNotExist())
        .andExpect(jsonPath("$.items[0].pdf_sha256").doesNotExist())
        .andExpect(jsonPath("$.items[0].content").doesNotExist())
        .andExpect(jsonPath("$.next_cursor").value("cursor-siguiente"));
  }

  @Test
  void passesTheLimitAndTheCursorToTheUseCase() throws Exception {
    when(list.execute(any(Caller.class), eq(5), eq("abc.def")))
        .thenReturn(new Page<>(List.of(), null));

    mvc.perform(get(MINUTES + "?limit=5&cursor=abc.def").with(member("MINUTES_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
    verify(list).execute(any(Caller.class), eq(5), eq("abc.def"));
  }

  @Test
  void aCursorOfAnotherListIsAnsweredWithCursorInvalid() throws Exception {
    when(list.execute(any(Caller.class), eq(20), eq("x.y")))
        .thenThrow(new DomainException(ErrorCode.CURSOR_INVALID));

    mvc.perform(get(MINUTES + "?cursor=x.y").with(member("MINUTES_VIEW")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void aLimitOutOfRangeIsRefusedBeforeTheUseCase() throws Exception {
    mvc.perform(get(MINUTES + "?limit=0").with(member("MINUTES_VIEW")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(MINUTES + "?limit=101").with(member("MINUTES_VIEW")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void withoutTheViewPermissionTheListIsForbidden() throws Exception {
    mvc.perform(get(MINUTES).with(member("MINUTES_GENERATE"))).andExpect(status().isForbidden());
  }

  @Test
  void withoutATokenTheListIsUnauthorized() throws Exception {
    mvc.perform(get(MINUTES)).andExpect(status().isUnauthorized());
  }
}
