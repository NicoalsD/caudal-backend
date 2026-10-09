package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.user.NormalizedPassword;
import co.caudal.domain.user.PasswordPolicy;
import co.caudal.domain.user.PasswordPolicyViolationException;
import co.caudal.domain.user.Username;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommonPasswordsLoaderTest {

  @Test
  void loadsTheListWithoutCommentsOrBlankLines() {
    Set<String> common = CommonPasswordsLoader.load();

    assertThat(common).contains("password1234", "123456789012", "contraseña123");
    assertThat(common).noneMatch(line -> line.isBlank() || line.startsWith("#"));
  }

  @Test
  void everyEntryReachesTheMinimumLengthSoItCanMatter() {
    assertThat(CommonPasswordsLoader.load())
        .allSatisfy(
            entry -> assertThat(NormalizedPassword.of(entry).length()).isGreaterThanOrEqualTo(12));
  }

  @Test
  void policyBuiltFromTheListRejectsACommonPassword() {
    PasswordPolicy policy = new PasswordPolicy(CommonPasswordsLoader.load());

    Assertions.assertThrows(
        PasswordPolicyViolationException.class,
        () -> policy.check(NormalizedPassword.of("Qwerty123456"), Username.parse("test.board")));
  }
}
