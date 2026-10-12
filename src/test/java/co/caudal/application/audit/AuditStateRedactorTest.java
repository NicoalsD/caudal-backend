package co.caudal.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditStateRedactorTest {

  @Test
  void aNullStateStaysNull() {
    assertThat(AuditStateRedactor.redact(null)).isNull();
  }

  @Test
  void secretsAndPersonalDataAreHiddenAtAnyDepth() {
    Map<String, Object> state =
        Map.of(
            "status", "LOCKED",
            "password_hash", "$argon2id$...",
            "refresh_token", "abc",
            "full_name", "Persona de Prueba",
            "last_login_ip_hmac", "9f2c",
            "nested", Map.of("api_key", "k", "role", "OPERATOR"),
            "items", List.of(Map.of("email", "a@b.co")));

    Map<String, Object> redacted = AuditStateRedactor.redact(state);

    assertThat(redacted)
        .containsEntry("status", "LOCKED")
        .containsEntry("password_hash", AuditStateRedactor.REDACTED)
        .containsEntry("refresh_token", AuditStateRedactor.REDACTED)
        .containsEntry("full_name", AuditStateRedactor.REDACTED)
        .containsEntry("last_login_ip_hmac", AuditStateRedactor.REDACTED);
    assertThat(redacted.get("nested"))
        .isEqualTo(Map.of("api_key", AuditStateRedactor.REDACTED, "role", "OPERATOR"));
    assertThat(redacted.get("items"))
        .isEqualTo(List.of(Map.of("email", AuditStateRedactor.REDACTED)));
  }

  @Test
  void ordinaryKeysThatContainTwoLettersAreKept() {
    Map<String, Object> state = Map.of("tipo", "A", "ship", "B", "ip", "C");

    assertThat(AuditStateRedactor.redact(state))
        .containsEntry("tipo", "A")
        .containsEntry("ship", "B")
        .containsEntry("ip", AuditStateRedactor.REDACTED);
  }
}
