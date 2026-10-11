package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.mfa.SealedSecret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherAdapterTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  private final AesGcmSecretCipherAdapter cipher =
      new AesGcmSecretCipherAdapter(new MfaProperties(KEY, "k1", "CAUDAL"));

  @Test
  void decryptsWhatItEncryptedForTheSameOwner() {
    SealedSecret sealed = cipher.encrypt(SECRET, "user-1");

    assertThat(sealed.keyId()).isEqualTo("k1");
    assertThat(sealed.ciphertext()).hasSize(12 + SECRET.length + 16);
    assertThat(cipher.decrypt(sealed, "user-1")).isEqualTo(SECRET);
  }

  @Test
  void usesANewNonceEachTime() {
    assertThat(cipher.encrypt(SECRET, "user-1").ciphertext())
        .isNotEqualTo(cipher.encrypt(SECRET, "user-1").ciphertext());
  }

  @Test
  void aMovedAlteredOrForeignCiphertextDoesNotDecrypt() {
    SealedSecret sealed = cipher.encrypt(SECRET, "user-1");
    byte[] altered = sealed.ciphertext().clone();
    altered[altered.length - 1] ^= 1;

    assertThatThrownBy(() -> cipher.decrypt(sealed, "user-2"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cipher.decrypt(new SealedSecret(altered, "k1"), "user-1"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cipher.decrypt(new SealedSecret(sealed.ciphertext(), "k0"), "user-1"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cipher.decrypt(new SealedSecret(new byte[12], "k1"), "user-1"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAMissingOrBadlySizedKeyWithoutPrintingIt() {
    assertThatThrownBy(() -> new MfaProperties(null, "k1", "CAUDAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MfaProperties(KEY, " ", "CAUDAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MfaProperties("bm90IDMyIGJ5dGVz", "k1", "CAUDAL"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("bm90");
    assertThatThrownBy(() -> new MfaProperties("***", "k1", "CAUDAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new MfaProperties(KEY, "k1", "CAUDAL").toString()).doesNotContain(KEY);
  }
}
