package co.caudal.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies the hexagonal architecture (docs/Arquitectura.md, section 2.3, rules ARCH-01 to
 * ARCH-11). Rules whose subject does not exist yet allow an empty selection, so they start checking
 * as soon as the first matching class is added.
 */
@AnalyzeClasses(packages = "co.caudal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN = "co.caudal.domain..";
  private static final String APPLICATION = "co.caudal.application..";
  private static final String API = "co.caudal.api..";
  private static final String INFRASTRUCTURE = "co.caudal.infrastructure..";
  private static final String SHARED = "co.caudal.shared..";
  private static final String PERSISTENCE = "co.caudal.infrastructure.persistence..";
  private static final String PORT_OUT = "co.caudal.application.port.out..";
  private static final String SYSTEM_CLOCK = "co.caudal.shared.time.SystemClock";

  @ArchTest
  static final ArchRule arch01DomainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..")
          .allowEmptyShould(true)
          .as("ARCH-01 the domain does not depend on Spring, JPA or Jackson");

  @ArchTest
  static final ArchRule arch02DomainDoesNotDependOnOuterLayers =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, APPLICATION, INFRASTRUCTURE)
          .allowEmptyShould(true)
          .as("ARCH-02 the domain does not depend on api, application or infrastructure");

  @ArchTest
  static final ArchRule arch03ApplicationDoesNotDependOnApiOrInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, INFRASTRUCTURE)
          .allowEmptyShould(true)
          .as("ARCH-03 the application does not depend on api or infrastructure");

  @ArchTest
  static final ArchRule arch04ApiDoesNotDependOnInfrastructureOrDomain =
      noClasses()
          .that()
          .resideInAPackage(API)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(INFRASTRUCTURE, DOMAIN)
          .allowEmptyShould(true)
          .as("ARCH-04 the api only uses application and shared");

  @ArchTest
  static final ArchRule arch05NoCyclesBetweenTopLevelPackages =
      slices()
          .matching("co.caudal.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true)
          .as("ARCH-05 no cycles between the top-level packages");

  @ArchTest
  static final ArchRule arch06ControllersLiveInApiAndEndWithController =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(API)
          .andShould()
          .haveSimpleNameEndingWith("Controller")
          .allowEmptyShould(true)
          .as("ARCH-06 controllers are in api and their name ends with Controller");

  @ArchTest
  static final ArchRule arch07JpaOnlyInPersistence =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .or()
          .areAssignableTo(JpaRepository.class)
          .should()
          .resideInAPackage(PERSISTENCE)
          .allowEmptyShould(true)
          .as(
              "ARCH-07 JPA entities and Spring Data repositories live in infrastructure.persistence");

  @ArchTest
  static final ArchRule arch08PortImplementationsAreAdaptersInInfrastructure =
      classes()
          .that()
          .implement(simpleNameEndingWith("Port"))
          .and()
          .areNotInterfaces()
          .should()
          .haveSimpleNameEndingWith("Adapter")
          .orShould()
          .haveSimpleNameEndingWith("Proxy")
          .andShould()
          .resideInAPackage(INFRASTRUCTURE)
          .allowEmptyShould(true)
          .as("ARCH-08 port implementations are *Adapter or *Proxy and live in infrastructure");

  @ArchTest
  static final ArchRule arch09SharedDoesNotDependOnOtherPackages =
      noClasses()
          .that()
          .resideInAPackage(SHARED)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, APPLICATION, DOMAIN, INFRASTRUCTURE)
          .allowEmptyShould(true)
          .as("ARCH-09 shared does not depend on other CAUDAL packages");

  @ArchTest
  static final ArchRule arch10OutputPortsAreInterfacesNamedPort =
      classes()
          .that()
          .resideInAPackage(PORT_OUT)
          .should()
          .beInterfaces()
          .andShould()
          .haveSimpleNameEndingWith("Port")
          .allowEmptyShould(true)
          .as("ARCH-10 output ports are interfaces named *Port in application.port.out");

  @ArchTest
  static final ArchRule arch11OnlySystemClockReadsSystemTime =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(SYSTEM_CLOCK)
          .should()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(LocalDate.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .orShould()
          .callMethod(ZonedDateTime.class, "now")
          .orShould()
          .callMethod(System.class, "currentTimeMillis")
          .allowEmptyShould(true)
          .as("ARCH-11 only SystemClock reads the system time");
}
