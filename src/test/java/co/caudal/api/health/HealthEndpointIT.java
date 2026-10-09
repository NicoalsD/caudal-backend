package co.caudal.api.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIT {

  @Autowired private MockMvc mvc;

  @Test
  void healthIsUpWithoutDetails() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void otherActuatorEndpointsAreNotExposed() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
  }
}
