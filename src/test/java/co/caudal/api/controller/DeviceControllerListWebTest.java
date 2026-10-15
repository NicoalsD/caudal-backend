package co.caudal.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.command.CommandBus;
import co.caudal.application.device.DeviceKeyValidityView;
import co.caudal.application.device.DeviceListItemView;
import co.caudal.application.device.ListDevicesUseCase;
import co.caudal.application.pagination.Page;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.WebSecurityTestConfig;
import java.time.Instant;
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

@WebMvcTest(controllers = DeviceController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class DeviceControllerListWebTest {

  private static final String DEVICES = "/api/v1/devices";
  private static final UUID ADMIN = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final UUID DEVICE = UUID.fromString("0190f3a2-0000-7000-8000-0000000000d1");
  private static final UUID KEY = UUID.fromString("0190f3a2-0000-7000-8000-0000000000e1");

  @Autowired private MockMvc mvc;
  @MockitoBean private CommandBus commands;
  @MockitoBean private ListDevicesUseCase list;

  private static RequestPostProcessor withAuthority(String authority, String role) {
    return jwt()
        .jwt(
            j ->
                j.subject(ADMIN.toString())
                    .claim("role", role)
                    .claim("aqueduct_id", AQUEDUCT.toString()))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static RequestPostProcessor boardAdmin() {
    return withAuthority("DEVICE_MANAGE", "BOARD_ADMIN");
  }

  private static DeviceListItemView withKey() {
    return new DeviceListItemView(
        DEVICE,
        "LEVEL_SENSOR",
        "Sensor del tanque principal",
        UUID.fromString("0190f3a2-0000-7000-8000-0000000000f1"),
        null,
        "ACTIVE",
        Instant.parse("2026-10-10T13:00:00Z"),
        Instant.parse("2026-10-14T08:30:00Z"),
        new DeviceKeyValidityView(
            KEY, Instant.parse("2026-10-10T13:05:00Z"), Instant.parse("2026-10-20T00:00:00Z")));
  }

  @Test
  void listsTheDevicesWithTheValidKeyAndNeverItsMaterial() throws Exception {
    when(list.execute(any(), anyInt(), any()))
        .thenReturn(new Page<>(List.of(withKey()), "cursor-siguiente"));

    mvc.perform(get(DEVICES).with(boardAdmin()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items[0].id").value(DEVICE.toString()))
        .andExpect(jsonPath("$.items[0].kind").value("LEVEL_SENSOR"))
        .andExpect(jsonPath("$.items[0].name").value("Sensor del tanque principal"))
        .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.items[0].created_at").value("2026-10-10T13:00:00Z"))
        .andExpect(jsonPath("$.items[0].last_seen_at").value("2026-10-14T08:30:00Z"))
        .andExpect(jsonPath("$.items[0].active_key.id").value(KEY.toString()))
        .andExpect(jsonPath("$.items[0].active_key.valid_from").value("2026-10-10T13:05:00Z"))
        .andExpect(jsonPath("$.items[0].active_key.valid_until").value("2026-10-20T00:00:00Z"))
        .andExpect(jsonPath("$.items[0].public_key").doesNotExist())
        .andExpect(jsonPath("$.items[0].fingerprint").doesNotExist())
        .andExpect(jsonPath("$.items[0].active_key.public_key").doesNotExist())
        .andExpect(jsonPath("$.items[0].active_key.fingerprint").doesNotExist())
        .andExpect(jsonPath("$.next_cursor").value("cursor-siguiente"));
  }

  @Test
  void aDeviceWithoutValidKeyShowsNullAndTheDefaultPageSizeIsTwenty() throws Exception {
    when(list.execute(any(), anyInt(), any()))
        .thenReturn(
            new Page<>(
                List.of(
                    new DeviceListItemView(
                        DEVICE,
                        "GATEWAY",
                        "Gateway veredal",
                        null,
                        null,
                        "ACTIVE",
                        Instant.parse("2026-10-10T13:00:00Z"),
                        null,
                        null)),
                null));

    mvc.perform(get(DEVICES).with(boardAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].active_key").doesNotExist())
        .andExpect(jsonPath("$.items[0].last_seen_at").doesNotExist())
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    verify(list).execute(any(), eq(20), isNull());
  }

  @Test
  void passesTheLimitAndTheCursorToTheUseCase() throws Exception {
    when(list.execute(any(), anyInt(), any())).thenReturn(new Page<>(List.of(), null));

    mvc.perform(get(DEVICES).param("limit", "5").param("cursor", "abc").with(boardAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));

    verify(list).execute(any(), eq(5), eq("abc"));
  }

  @Test
  void aLimitOutOfRangeAnswers400BeforeReadingAnything() throws Exception {
    mvc.perform(get(DEVICES).param("limit", "0").with(boardAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    mvc.perform(get(DEVICES).param("limit", "101").with(boardAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(list);
  }

  @Test
  void anAlteredCursorAnswers400CursorInvalid() throws Exception {
    when(list.execute(any(), anyInt(), any()))
        .thenThrow(new DomainException(ErrorCode.CURSOR_INVALID));

    mvc.perform(get(DEVICES).param("cursor", "alterado").with(boardAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void withoutAccessTokenAnswers401() throws Exception {
    mvc.perform(get(DEVICES))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    verifyNoInteractions(list);
  }

  @Test
  void withoutThePermissionAnswers403() throws Exception {
    mvc.perform(get(DEVICES).with(withAuthority("INCIDENT_VIEW", "OPERATOR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    verifyNoInteractions(list);
  }
}
