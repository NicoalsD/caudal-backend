package co.caudal.application.simulation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DemoImportGuardTest {

  private static final UUID DEMO = UUID.randomUUID();
  private static final UUID REAL = UUID.randomUUID();
  private static final String SHA = "a".repeat(64);

  private final AqueductPort aqueducts = mock(AqueductPort.class);
  private final ImportBatchPort imports = mock(ImportBatchPort.class);
  private final DemoImportGuard guard = new DemoImportGuard(aqueducts, imports);

  private static ImportRequest request(UUID session, UUID target, int rows) {
    return new ImportRequest(
        new Caller(UUID.randomUUID(), session, "PROJECT_TEAM", null),
        target,
        "escenario",
        42L,
        "sim-1",
        SHA,
        rows);
  }

  private static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void aDemoAqueductOfTheSessionWithANewFileImports() {
    when(aqueducts.findById(DEMO))
        .thenReturn(
            Optional.of(new AqueductRef(DEMO, "demo", "Demo", "America/Bogota", true, true)));

    assertThatCode(() -> guard.check(request(DEMO, DEMO, 5000))).doesNotThrowAnyException();
  }

  @Test
  void aRealOrForeignAqueductIsDemoOnly() {
    when(aqueducts.findById(REAL))
        .thenReturn(
            Optional.of(new AqueductRef(REAL, "real", "Real", "America/Bogota", false, true)));

    assertThatThrownBy(() -> guard.check(request(REAL, REAL, 1)))
        .extracting(DemoImportGuardTest::codeOf)
        .isEqualTo(ErrorCode.DEMO_ONLY);
    assertThatThrownBy(() -> guard.check(request(REAL, DEMO, 1)))
        .extracting(DemoImportGuardTest::codeOf)
        .isEqualTo(ErrorCode.DEMO_ONLY);
    assertThatThrownBy(() -> guard.check(request(DEMO, DEMO, 1)))
        .extracting(DemoImportGuardTest::codeOf)
        .isEqualTo(ErrorCode.DEMO_ONLY);
  }

  @Test
  void tooManyRowsOrARepeatedFileAreRefused() {
    when(aqueducts.findById(DEMO))
        .thenReturn(
            Optional.of(new AqueductRef(DEMO, "demo", "Demo", "America/Bogota", true, true)));

    assertThatThrownBy(() -> guard.check(request(DEMO, DEMO, 5001)))
        .extracting(DemoImportGuardTest::codeOf)
        .isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
    when(imports.fileImported(SHA)).thenReturn(true);
    assertThatThrownBy(() -> guard.check(request(DEMO, DEMO, 1)))
        .extracting(DemoImportGuardTest::codeOf)
        .isEqualTo(ErrorCode.CONFLICT);
  }
}
