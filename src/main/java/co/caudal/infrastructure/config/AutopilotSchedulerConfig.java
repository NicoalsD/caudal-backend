package co.caudal.infrastructure.config;

import co.caudal.application.autopilot.AutopilotRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the autopilot every {@code AUTOPILOT_INTERVAL_SECONDS} seconds (60 by default). It is off
 * when {@code AUTOPILOT_ENABLED=false}; the test profile turns it off, and the tests force a cycle
 * through {@code POST /api/v1/autopilot/run}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "caudal.autopilot.enabled", havingValue = "true")
class AutopilotSchedulerConfig {

  @Component
  @ConditionalOnProperty(name = "caudal.autopilot.enabled", havingValue = "true")
  static class AutopilotScheduler {

    private final AutopilotRunner runner;

    AutopilotScheduler(AutopilotRunner runner) {
      this.runner = runner;
    }

    @Scheduled(
        initialDelayString = "PT${caudal.autopilot.interval-seconds}S",
        fixedDelayString = "PT${caudal.autopilot.interval-seconds}S")
    void tick() {
      runner.runAll();
    }
  }
}
