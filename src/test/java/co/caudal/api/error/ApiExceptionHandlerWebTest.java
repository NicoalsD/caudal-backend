package co.caudal.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.filter.RequestIdFilter;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerWebTest.ProbeController.class)
@Import({
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class,
  ApiExceptionHandlerWebTest.ProbeController.class
})
class ApiExceptionHandlerWebTest {

  @Autowired private MockMvc mvc;

  @Test
  void domainExceptionUsesCanonicalFormat() throws Exception {
    mvc.perform(get("/probe/domain").header(RequestIdFilter.HEADER, "req-123"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(header().string(RequestIdFilter.HEADER, "req-123"))
        .andExpect(jsonPath("$.error.code").value("GAUGE_OUT_OF_RANGE"))
        .andExpect(
            jsonPath("$.error.message")
                .value("La lectura está fuera del rango de la regla del tanque."))
        .andExpect(jsonPath("$.error.details.gauge_max").value("5,00"))
        .andExpect(jsonPath("$.error.request_id").value("req-123"));
  }

  @Test
  void messageArgumentsFillTheTemplate() throws Exception {
    mvc.perform(get("/probe/reason"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.error.message").value("Escribe el motivo (entre 10 y 500 caracteres)."));
  }

  @Test
  void invalidBodyListsFieldsInSnakeCase() throws Exception {
    mvc.perform(
            post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"full_name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='full_name')].code")
                .value("FIELD_TOO_SHORT"))
        .andExpect(
            jsonPath("$.error.details.fields[?(@.field=='tank_code')].code")
                .value("FIELD_REQUIRED"));
  }

  @Test
  void malformedJsonIsValidationError() throws Exception {
    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void unsupportedMediaType() throws Exception {
    mvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void unknownRouteIsNotFound() throws Exception {
    mvc.perform(get("/probe/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void unexpectedErrorHidesTheCause() throws Exception {
    mvc.perform(get("/probe/crash"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(
            jsonPath("$.error.message").value("Ocurrió un error. Intenta de nuevo más tarde."))
        .andExpect(jsonPath("$.error.details").isEmpty())
        .andExpect(jsonPath("$.error.request_id").isNotEmpty());
  }

  @RestController
  static class ProbeController {

    private static final int REASON_MIN = 10;
    private static final int REASON_MAX = 500;

    @GetMapping("/probe/domain")
    void domain() {
      throw new DomainException(ErrorCode.GAUGE_OUT_OF_RANGE, Map.of("gauge_max", "5,00"));
    }

    @GetMapping("/probe/reason")
    void reason() {
      throw new DomainException(ErrorCode.REASON_REQUIRED, Map.of(), REASON_MIN, REASON_MAX);
    }

    @PostMapping("/probe/body")
    void body(@Valid @RequestBody ProbeRequest request) {
      // Only validation is exercised.
    }

    @GetMapping("/probe/crash")
    void crash() {
      throw new IllegalStateException("password=leaked-secret");
    }
  }

  record ProbeRequest(
      @NotNull @Size(min = 2, max = 80) String fullName, @NotNull String tankCode) {}
}
