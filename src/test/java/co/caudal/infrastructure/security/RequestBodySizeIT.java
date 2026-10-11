package co.caudal.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.shared.FieldLimits;
import co.caudal.support.SessionIT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Bodies over their limit answer 413 before they reach the controllers. */
class RequestBodySizeIT extends SessionIT {

  @Test
  void aBodyOverSixtyFourKibibytesIsRefused() throws Exception {
    String big = "{\"username\":\"" + "a".repeat(FieldLimits.BODY_MAX_BYTES) + "\"}";

    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(big))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
  }

  @Test
  void eachPathHasItsLimit() {
    Assertions.assertThat(RequestBodySizeFilter.limitFor("/api/v1/imports/readings"))
        .isEqualTo(FieldLimits.IMPORT_BODY_MAX_BYTES);
    Assertions.assertThat(RequestBodySizeFilter.limitFor("/api/v1/devices/telemetry"))
        .isEqualTo(FieldLimits.TELEMETRY_BODY_MAX_BYTES);
    Assertions.assertThat(RequestBodySizeFilter.limitFor("/api/v1/readings/batch"))
        .isEqualTo(FieldLimits.BODY_MAX_BYTES);
  }
}
