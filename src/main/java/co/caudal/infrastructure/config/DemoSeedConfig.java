package co.caudal.infrastructure.config;

import co.caudal.application.demo.DemoUsersSeeder;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the demo accounts at startup when {@code SEED_DEMO_DATA=true} and a password is set. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoSeedProperties.class)
class DemoSeedConfig {

  private static final Logger LOG = LoggerFactory.getLogger(DemoSeedConfig.class);

  @Bean
  DemoUsersSeeder demoUsersSeeder(
      AqueductPort aqueducts,
      UserAccountPort accounts,
      MembershipAdminPort memberships,
      PasswordHasherPort passwords,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new DemoUsersSeeder(aqueducts, accounts, memberships, passwords, unitOfWork, clock);
  }

  @Bean
  ApplicationRunner demoUsersRunner(DemoUsersSeeder seeder, DemoSeedProperties properties) {
    return args -> {
      if (!properties.enabled()
          || properties.userPassword() == null
          || properties.userPassword().isBlank()) {
        return;
      }
      int created = seeder.seed(properties.aqueductSlug(), properties.userPassword());
      LOG.info("Demo accounts created: {}", created);
    };
  }
}
