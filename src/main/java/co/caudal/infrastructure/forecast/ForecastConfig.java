package co.caudal.infrastructure.forecast;

import co.caudal.application.port.out.ForecastServicePort;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** The client of the forecast service. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IaProperties.class)
class ForecastConfig {

  @Bean
  ForecastServicePort forecastService(IaProperties properties, JsonMapper json) {
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return new IaForecastClientAdapter(http, properties, json);
  }
}
