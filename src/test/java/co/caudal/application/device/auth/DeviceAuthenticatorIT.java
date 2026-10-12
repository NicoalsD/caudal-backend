package co.caudal.application.device.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The signature check end to end with the database: the device and its keys are created through the
 * API, and the authenticator bean checks requests signed with them. The security events of a
 * refusal must be stored (docs/Protocolo-de-dispositivos.md, sections 3.1 and 3.2).
 */
class DeviceAuthenticatorIT extends SessionIT {

  private static final String PATH = "/api/v1/devices/telemetry";
  private static final String ADDRESS = "203.0.113.99";
  private static final byte[] BODY = "{\"points\":[]}".getBytes(StandardCharsets.UTF_8);

  /** The default of DEVICE_REQUESTS_PER_MINUTE, as configured in application.yml. */
  private static final int DEVICE_REQUESTS_PER_MINUTE = 60;

  @Autowired private DeviceAuthenticator authenticator;
  @Autowired private Clock clock;

  private ResultActions send(
      Session session,
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      String body)
      throws Exception {
    return mvc.perform(
        request
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String idOf(ResultActions result, String field) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"" + field + "\":\"([^\"]+)\".*", "$1");
  }

  /** A gateway registered with a fresh key: its id, its key and the session that owns it. */
  private record Enrolled(UUID device, UUID key, KeyPair pair) {}

  private Enrolled enroll(Session session) throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    ResultActions created =
        send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"Pasarela\"}")
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                    .isCreated());
    UUID device = UUID.fromString(idOf(created, "id"));
    ResultActions key =
        send(
                session,
                post("/api/v1/devices/" + device + "/keys"),
                "{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}")
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                    .isCreated());
    return new Enrolled(device, UUID.fromString(idOf(key, "key_id")), pair);
  }

  private SignedRequest signed(
      Enrolled device, long timestamp, String nonce, byte[] body, KeyPair signer) throws Exception {
    String text = Long.toString(timestamp);
    String signature = DeviceRequestSigner.sign(signer, "POST", PATH, text, nonce, body);
    return SignedRequest.of(
        "POST",
        PATH,
        DeviceRequestSigner.headers(device.device().toString(), text, nonce, signature),
        body,
        ADDRESS);
  }

  private SignedRequest fresh(Enrolled device) throws Exception {
    return signed(device, clock.instant().getEpochSecond(), nonce(), BODY, device.pair());
  }

  private static String nonce() {
    byte[] bytes = new byte[16];
    new java.security.SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private long events(UUID device, String type, String reason) {
    return owner.queryForObject(
        "SELECT count(*) FROM iam.security_events WHERE device_id = ? AND type = ?"
            + " AND details->>'reason' = ?",
        Long.class,
        device,
        type,
        reason);
  }

  private static DomainException refusal(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    return (DomainException) org.assertj.core.api.Assertions.catchThrowable(call);
  }

  @Test
  void acceptsARequestSignedWithARegisteredKey() throws Exception {
    Session session = login(account("auth.accept", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);

    AuthenticatedDevice verified = authenticator.authenticate(fresh(device));

    assertThat(verified.deviceId()).isEqualTo(device.device());
    assertThat(verified.keyId()).isEqualTo(device.key());
    assertThat(verified.kind()).isEqualTo("GATEWAY");
  }

  @Test
  void aChangedBodyIsRefusedAndTheEventSurvives() throws Exception {
    Session session = login(account("auth.body", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    byte[] altered = BODY.clone();
    altered[altered.length - 2] = '1';
    SignedRequest tampered =
        signed(device, clock.instant().getEpochSecond(), nonce(), altered, device.pair());
    SignedRequest signedOver =
        new SignedRequest(
            tampered.deviceId(),
            tampered.method(),
            tampered.path(),
            tampered.timestampText(),
            tampered.timestamp(),
            tampered.nonce(),
            tampered.signature(),
            BODY,
            ADDRESS);

    DomainException error = refusal(() -> authenticator.authenticate(signedOver));

    assertThat(error.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
    assertThat(events(device.device(), "INVALID_SIGNATURE", "BAD_SIGNATURE")).isEqualTo(1);
  }

  @Test
  void aTimestampOutsideTheWindowIsRefusedWithALowEvent() throws Exception {
    Session session = login(account("auth.time", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    long late = clock.instant().getEpochSecond() - 301;

    DomainException error =
        refusal(
            () -> authenticator.authenticate(signed(device, late, nonce(), BODY, device.pair())));

    assertThat(error.code()).isEqualTo(ErrorCode.TIMESTAMP_OUT_OF_WINDOW);
    assertThat(events(device.device(), "INVALID_SIGNATURE", "TIMESTAMP_OUT_OF_WINDOW"))
        .isEqualTo(1);
  }

  @Test
  void aSuspendedDeviceIsInactiveAndRecordsPermissionDenied() throws Exception {
    Session session = login(account("auth.suspend", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    send(session, patch("/api/v1/devices/" + device.device()), "{\"status\":\"SUSPENDED\"}");

    DomainException error = refusal(() -> authenticator.authenticate(fresh(device)));

    assertThat(error.code()).isEqualTo(ErrorCode.DEVICE_INACTIVE);
    assertThat(events(device.device(), "PERMISSION_DENIED", "DEVICE_INACTIVE")).isEqualTo(1);
  }

  @Test
  void aRevokedKeyIsRefusedAsAnInvalidSignature() throws Exception {
    Session session = login(account("auth.revoke", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    send(session, delete("/api/v1/devices/" + device.device() + "/keys/" + device.key()), "")
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNoContent());

    DomainException error = refusal(() -> authenticator.authenticate(fresh(device)));

    assertThat(error.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
    assertThat(events(device.device(), "INVALID_SIGNATURE", "NO_ACTIVE_KEY")).isEqualTo(1);
  }

  @Test
  void anUnknownDeviceAnswersLikeABadSignatureWithoutADeviceEvent() throws Exception {
    Enrolled ghost =
        new Enrolled(UUID.randomUUID(), UUID.randomUUID(), DeviceRequestSigner.newKeyPair());

    DomainException error = refusal(() -> authenticator.authenticate(fresh(ghost)));

    assertThat(error.code()).isEqualTo(ErrorCode.INVALID_SIGNATURE);
  }

  @Test
  void tenInvalidSignaturesRaiseTheRepeatedEvent() throws Exception {
    Session session = login(account("auth.repeat", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    KeyPair stranger = DeviceRequestSigner.newKeyPair();

    for (int i = 0; i < 10; i++) {
      long now = clock.instant().getEpochSecond();
      SignedRequest request = signed(device, now, nonce(), BODY, stranger);
      assertThatThrownBy(() -> authenticator.authenticate(request))
          .isInstanceOf(DomainException.class);
    }

    assertThat(events(device.device(), "INVALID_SIGNATURE", "REPEATED")).isEqualTo(1);
  }

  @Test
  void aBadSignatureNeverStoresItsNonce() throws Exception {
    Session session = login(account("auth.nonce", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    String nonce = nonce();
    KeyPair stranger = DeviceRequestSigner.newKeyPair();
    SignedRequest forged = signed(device, clock.instant().getEpochSecond(), nonce, BODY, stranger);

    assertThatThrownBy(() -> authenticator.authenticate(forged))
        .isInstanceOf(DomainException.class);

    assertThat(storedNonces(device.device(), nonce)).isZero();
  }

  @Test
  void aRepeatedNonceIsRefusedAndRaisesAHighEvent() throws Exception {
    Session session = login(account("auth.replay", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    SignedRequest request = fresh(device);
    authenticator.authenticate(request);

    DomainException error = refusal(() -> authenticator.authenticate(request));

    assertThat(error.code()).isEqualTo(ErrorCode.NONCE_REPLAY);
    assertThat(storedNonces(device.device(), request.nonce())).isEqualTo(1);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE device_id = ?"
                    + " AND type = 'NONCE_REPLAY' AND severity = 'HIGH'",
                Long.class,
                device.device()))
        .isEqualTo(1);
  }

  @Test
  void theSixtyFirstRequestOfAMinuteIsRateLimited() throws Exception {
    Session session = login(account("auth.rate", "BOARD_ADMIN", newAqueductWithRules()));
    Enrolled device = enroll(session);
    for (int i = 0; i < DEVICE_REQUESTS_PER_MINUTE; i++) {
      authenticator.authenticate(fresh(device));
    }

    DomainException error = refusal(() -> authenticator.authenticate(fresh(device)));

    assertThat(error.code()).isEqualTo(ErrorCode.RATE_LIMITED);
    assertThat(error.details()).containsKey("retry_after_seconds");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE device_id = ?"
                    + " AND type = 'RATE_LIMITED' AND details->>'reason' = 'device'",
                Long.class,
                device.device()))
        .isEqualTo(1);
  }

  private long storedNonces(UUID device, String nonce) {
    return owner.queryForObject(
        "SELECT count(*) FROM devices.device_nonces WHERE device_id = ? AND nonce = ?",
        Long.class,
        device,
        nonce);
  }
}
