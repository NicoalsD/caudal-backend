package co.caudal.infrastructure.config;

import co.caudal.application.device.DeviceCommandHandlers;
import co.caudal.application.device.DeviceKeyCommandHandlers;
import co.caudal.application.device.auth.DeviceAuthenticator;
import co.caudal.application.device.telemetry.DeviceTelemetryService;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.TelemetryPayloadPort;
import co.caudal.application.port.out.TelemetryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.ReadingRecorder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the field devices. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeviceProperties.class)
class DeviceServicesConfig {

  @Bean
  DeviceCommandHandlers.Create createDeviceCommandHandler(
      DevicePort devices, NetworkPort network, UnitOfWorkPort unitOfWork) {
    return new DeviceCommandHandlers.Create(devices, network, unitOfWork);
  }

  @Bean
  DeviceCommandHandlers.UpdateStatus updateDeviceCommandHandler(
      DevicePort devices, UnitOfWorkPort unitOfWork) {
    return new DeviceCommandHandlers.UpdateStatus(devices, unitOfWork);
  }

  @Bean
  DeviceAuthenticator deviceAuthenticator(
      DevicePort devices,
      SecurityEventPort events,
      UnitOfWorkPort unitOfWork,
      PrivacyHasherPort hasher,
      RateLimiterPort rateLimiter,
      DeviceProperties properties,
      Clock clock) {
    return new DeviceAuthenticator(
        devices, events, unitOfWork, hasher, rateLimiter, properties.requestsPerMinute(), clock);
  }

  @Bean
  DeviceTelemetryService deviceTelemetryService(
      DeviceAuthenticator authenticator,
      TelemetryPayloadPort payloads,
      TelemetryPort telemetry,
      DevicePort devices,
      ReadingRecorder recorder,
      RuleSetPort ruleSets,
      Clock clock) {
    return new DeviceTelemetryService(
        authenticator, payloads, telemetry, devices, recorder, ruleSets, clock);
  }

  @Bean
  DeviceKeyCommandHandlers.Rotate rotateDeviceKeyCommandHandler(
      DevicePort devices, UnitOfWorkPort unitOfWork, DeviceProperties properties) {
    return new DeviceKeyCommandHandlers.Rotate(devices, unitOfWork, properties.keyRotationDays());
  }

  @Bean
  DeviceKeyCommandHandlers.Revoke revokeDeviceKeyCommandHandler(
      DevicePort devices, UnitOfWorkPort unitOfWork) {
    return new DeviceKeyCommandHandlers.Revoke(devices, unitOfWork);
  }
}
