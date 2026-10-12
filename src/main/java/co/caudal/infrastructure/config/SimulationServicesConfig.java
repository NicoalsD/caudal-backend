package co.caudal.infrastructure.config;

import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.SimulatedHistoryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.ReadingRecorder;
import co.caudal.application.simulation.DemoImportGuard;
import co.caudal.application.simulation.ImportHistoryCommandHandler;
import co.caudal.application.simulation.ImportReadingsCommandHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Imports of simulated history into demo aqueducts. */
@Configuration(proxyBeanMethods = false)
class SimulationServicesConfig {

  @Bean
  DemoImportGuard demoImportGuard(AqueductPort aqueducts, ImportBatchPort imports) {
    return new DemoImportGuard(aqueducts, imports);
  }

  @Bean
  ImportReadingsCommandHandler importReadingsCommandHandler(
      DemoImportGuard guard,
      RuleSetPort ruleSets,
      ImportBatchPort imports,
      ReadingRecorder recorder,
      UnitOfWorkPort unitOfWork) {
    return new ImportReadingsCommandHandler(guard, ruleSets, imports, recorder, unitOfWork);
  }

  @Bean
  ImportHistoryCommandHandler importHistoryCommandHandler(
      DemoImportGuard guard,
      ImportBatchPort imports,
      SimulatedHistoryPort history,
      CatalogPort catalogs,
      NetworkPort network,
      UnitOfWorkPort unitOfWork) {
    return new ImportHistoryCommandHandler(guard, imports, history, catalogs, network, unitOfWork);
  }
}
