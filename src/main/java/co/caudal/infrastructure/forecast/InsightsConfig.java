package co.caudal.infrastructure.forecast;

import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.InsightsServicePort;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client of the assistant of {@code caudal-ia}. It reuses the URL, the token and the thresholds
 * of the forecast client, with a circuit breaker of its own.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({InsightsProperties.class, AutopilotProperties.class})
class InsightsConfig {

  @Bean
  InsightsServicePort insightsService(
      IaProperties properties, InsightsProperties insights, JsonMapper json) {
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return new IaInsightsClientAdapter(http, properties, insights, json);
  }

  @Bean
  CircuitBreaker insightsCircuitBreaker(CircuitBreakerSettings settings, Clock clock) {
    return new CircuitBreaker(settings, clock, "Insights");
  }

  @Bean
  InsightsPort insights(
      InsightsServicePort insightsService,
      @Qualifier("insightsCircuitBreaker") CircuitBreaker breaker,
      Clock clock) {
    return new RemoteInsightsProxy(insightsService, breaker, clock);
  }
}
