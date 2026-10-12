package co.caudal.infrastructure.config;

import co.caudal.application.device.DeviceCommandHandlers;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases of the field devices. */
@Configuration(proxyBeanMethods = false)
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
}
