package co.caudal.infrastructure.time;

import co.caudal.shared.time.SystemClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes {@link SystemClock} as the {@link Clock} injected into use cases and adapters. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  Clock clock() {
    return SystemClock.getInstance();
  }
}
