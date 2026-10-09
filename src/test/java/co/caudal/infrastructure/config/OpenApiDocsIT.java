package co.caudal.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiDocsIT {

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  class Enabled {

    @Autowired private MockMvc mvc;

    @Test
    void publishesDocumentWithBearerAndDeviceSchemes() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.info.title").value("CAUDAL API"))
          .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
          .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
          .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
          .andExpect(jsonPath("$.components.securitySchemes.deviceSignature.in").value("header"))
          .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
      mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
  }

  @Nested
  @SpringBootTest(properties = "API_DOCS_ENABLED=false")
  @AutoConfigureMockMvc
  class Disabled {

    @Autowired private MockMvc mvc;

    @Test
    void hidesDocumentAndUi() throws Exception {
      mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
      mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
  }
}
