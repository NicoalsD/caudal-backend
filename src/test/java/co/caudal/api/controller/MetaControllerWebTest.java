package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.api.error.ApiExceptionHandler;
import co.caudal.api.filter.RequestIdFilter;
import co.caudal.infrastructure.config.MessagesConfig;
import co.caudal.shared.FieldLimits;
import co.caudal.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MetaController.class)
@Import({
  WebSecurityTestConfig.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  MessagesConfig.class
})
class MetaControllerWebTest {

  @Autowired private MockMvc mvc;

  @Test
  void anyoneReadsTheLimitsOfFieldLimits() throws Exception {
    mvc.perform(get("/api/v1/meta/constraints"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=3600, public"))
        .andExpect(jsonPath("$.username.min").value(FieldLimits.USERNAME_MIN))
        .andExpect(jsonPath("$.username.pattern").value(FieldLimits.USERNAME_PATTERN))
        .andExpect(jsonPath("$.password.max").value(FieldLimits.PASSWORD_MAX))
        .andExpect(jsonPath("$['reading.note'].max_lines").value(FieldLimits.NOTE_MAX_LINES))
        .andExpect(jsonPath("$['page.limit'].default").value(FieldLimits.PAGE_LIMIT_DEFAULT));
  }
}
