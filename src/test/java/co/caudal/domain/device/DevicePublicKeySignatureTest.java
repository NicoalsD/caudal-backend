package co.caudal.domain.device;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.support.DeviceRequestSigner;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class DevicePublicKeySignatureTest {

  private static final byte[] MESSAGE =
      "POST\n/api/v1/devices/telemetry\n1\nn\nh".getBytes(StandardCharsets.UTF_8);

  @Test
  void aSignatureOfTheMatchingKeyVerifies() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    DevicePublicKey key =
        DevicePublicKey.parse(DeviceRequestSigner.publicKeyText(pair)).orElseThrow();
    byte[] signature = signWith(pair, MESSAGE);

    assertThat(key.verifies(MESSAGE, signature)).isTrue();
  }

  @Test
  void aChangedMessageDoesNotVerify() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    DevicePublicKey key =
        DevicePublicKey.parse(DeviceRequestSigner.publicKeyText(pair)).orElseThrow();
    byte[] signature = signWith(pair, MESSAGE);
    byte[] changed = MESSAGE.clone();
    changed[0] ^= 1;

    assertThat(key.verifies(changed, signature)).isFalse();
  }

  @Test
  void aSignatureOfAnotherKeyDoesNotVerify() throws Exception {
    DevicePublicKey key =
        DevicePublicKey.parse(DeviceRequestSigner.publicKeyText(DeviceRequestSigner.newKeyPair()))
            .orElseThrow();
    byte[] signature = signWith(DeviceRequestSigner.newKeyPair(), MESSAGE);

    assertThat(key.verifies(MESSAGE, signature)).isFalse();
  }

  @Test
  void aSignatureOfTheWrongLengthIsSimplyNotValid() throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    DevicePublicKey key =
        DevicePublicKey.parse(DeviceRequestSigner.publicKeyText(pair)).orElseThrow();

    assertThat(key.verifies(MESSAGE, new byte[10])).isFalse();
  }

  private static byte[] signWith(KeyPair pair, byte[] message) throws Exception {
    java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
    signer.initSign(pair.getPrivate());
    signer.update(message);
    return signer.sign();
  }
}
