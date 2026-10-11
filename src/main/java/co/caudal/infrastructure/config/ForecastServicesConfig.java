package co.caudal.infrastructure.config;

import co.caudal.application.forecast.ForecastRunner;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.ForecasterPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use cases of the forecast, the tank status and the anomalies. */
@Configuration(proxyBeanMethods = false)
class ForecastServicesConfig {

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  ForecastRunner forecastRunner(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      AqueductPort aqueducts,
      ForecasterPort forecaster,
      ForecastRunPort runs,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ForecastRunner(
        network, ruleSets, readings, aqueducts, forecaster, runs, unitOfWork, clock);
  }
}
