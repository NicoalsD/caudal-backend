package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Listing the field devices of the aqueduct end to end (docs/API.md, section 16.9). */
class DeviceListIT extends SessionIT {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final SecureRandom RANDOM = new SecureRandom();

  private ResultActions list(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/devices" + query).header("Authorization", session.bearer()));
  }

  private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    return mvc.perform(
        request
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String keyIdOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"key_id\":\"([^\"]+)\".*", "$1");
  }

  /** A new Ed25519 public key in base64url, as the device sends it. */
  private static String newPublicKey() throws Exception {
    byte[] encoded =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
    return URL_ENCODER.encodeToString(
        Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
  }

  /** A random key of 32 bytes, inserted as the owner (the key itself is never listed). */
  private static String randomPublicKey() {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    return URL_ENCODER.encodeToString(raw);
  }

  private static Instant at(String instant) {
    return Instant.parse(instant);
  }

  private static OffsetDateTime offset(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /** Inserts a gateway registered at a given moment, as the owner. */
  UUID device(UUID aqueduct, UUID creator, String name, Instant createdAt) {
    return owner.queryForObject(
        "INSERT INTO devices.devices (aqueduct_id, kind, name, status, created_by, created_at)"
            + " VALUES (?, 'GATEWAY', ?, 'ACTIVE', ?, ?) RETURNING id",
        UUID.class,
        aqueduct,
        name,
        creator,
        offset(createdAt));
  }

  /** Inserts a key that is still open-ended (not revoked), as the owner. */
  UUID activeKey(UUID aqueduct, UUID device, Instant activatedAt) {
    return owner.queryForObject(
        "INSERT INTO devices.device_keys (aqueduct_id, device_id, public_key, fingerprint,"
            + " activated_at) VALUES (?, ?, ?, encode(sha256(convert_to(gen_random_uuid()::text,"
            + " 'UTF8')), 'hex'), ?) RETURNING id",
        UUID.class,
        aqueduct,
        device,
        randomPublicKey(),
        offset(activatedAt));
  }

  /** Inserts a key revoked at a given moment (past or future), as the owner. */
  UUID revokedKey(UUID aqueduct, UUID device, Instant activatedAt, Instant revokedAt) {
    return owner.queryForObject(
        "INSERT INTO devices.device_keys (aqueduct_id, device_id, public_key, fingerprint,"
            + " activated_at, revoked_at) VALUES (?, ?, ?, encode(sha256(convert_to("
            + "gen_random_uuid()::text, 'UTF8')), 'hex'), ?, ?) RETURNING id",
        UUID.class,
        aqueduct,
        device,
        randomPublicKey(),
        offset(activatedAt),
        offset(revokedAt));
  }

  @Test
  void listsTheNewestFirstWithTheValidKeyAndNeverItsMaterial() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account admin = account("device.list.admin", "BOARD_ADMIN", aqueduct);
    Session session = login(admin);
    UUID oldest = device(aqueduct, admin.userId(), "Gateway antiguo", at("2026-10-01T10:00:00Z"));
    UUID middle = device(aqueduct, admin.userId(), "Sensor medio", at("2026-10-02T10:00:00Z"));
    UUID newest = device(aqueduct, admin.userId(), "Motor nuevo", at("2026-10-03T10:00:00Z"));
    String publicKey = newPublicKey();
    String keyId =
        keyIdOf(
            send(
                    session,
                    post("/api/v1/devices/" + middle + "/keys"),
                    "{\"public_key\":\"" + publicKey + "\"}")
                .andExpect(status().isCreated()));
    String fingerprint =
        owner.queryForObject(
            "SELECT fingerprint FROM devices.device_keys WHERE id = ?",
            String.class,
            UUID.fromString(keyId));

    String body =
        list(session, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
            .andExpect(jsonPath("$.items[0].kind").value("GATEWAY"))
            .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].created_at").value("2026-10-03T10:00:00Z"))
            .andExpect(jsonPath("$.items[0].last_seen_at").doesNotExist())
            .andExpect(jsonPath("$.items[0].active_key").doesNotExist())
            .andExpect(jsonPath("$.items[1].id").value(middle.toString()))
            .andExpect(jsonPath("$.items[1].active_key.id").value(keyId))
            .andExpect(jsonPath("$.items[1].active_key.valid_from").isString())
            .andExpect(jsonPath("$.items[1].active_key.valid_until").doesNotExist())
            .andExpect(jsonPath("$.items[2].id").value(oldest.toString()))
            .andExpect(jsonPath("$.next_cursor").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain(publicKey)
        .doesNotContain(fingerprint)
        .doesNotContain("public_key")
        .doesNotContain("fingerprint")
        .doesNotContain("nonce")
        .doesNotContain("secret");
  }

  @Test
  void showsTheKeyThatIsValidNowAndItsEnd() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account admin = account("device.list.keys", "BOARD_ADMIN", aqueduct);
    Session session = login(admin);
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant graceEnd = now.plus(2, ChronoUnit.DAYS);

    UUID rotated = device(aqueduct, admin.userId(), "Sensor rotado", at("2026-10-03T10:00:00Z"));
    UUID graced = device(aqueduct, admin.userId(), "Sensor en gracia", at("2026-10-02T10:00:00Z"));
    UUID revoked = device(aqueduct, admin.userId(), "Sensor revocado", at("2026-10-01T10:00:00Z"));

    revokedKey(aqueduct, rotated, now.minus(9, ChronoUnit.DAYS), graceEnd);
    UUID current = activeKey(aqueduct, rotated, now.minus(1, ChronoUnit.DAYS));
    UUID onlyGrace = revokedKey(aqueduct, graced, now.minus(9, ChronoUnit.DAYS), graceEnd);
    revokedKey(aqueduct, revoked, now.minus(9, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));

    list(session, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(rotated.toString()))
        .andExpect(jsonPath("$.items[0].active_key.id").value(current.toString()))
        .andExpect(jsonPath("$.items[0].active_key.valid_until").doesNotExist())
        .andExpect(jsonPath("$.items[1].id").value(graced.toString()))
        .andExpect(jsonPath("$.items[1].active_key.id").value(onlyGrace.toString()))
        .andExpect(jsonPath("$.items[1].active_key.valid_until").value(graceEnd.toString()))
        .andExpect(jsonPath("$.items[2].id").value(revoked.toString()))
        .andExpect(jsonPath("$.items[2].active_key").doesNotExist());
  }

  @Test
  void pagesWithASignedCursorWithoutRepeatingRows() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account admin = account("device.list.pages", "BOARD_ADMIN", aqueduct);
    Session session = login(admin);
    UUID first = device(aqueduct, admin.userId(), "Primero", at("2026-10-01T10:00:00Z"));
    UUID second = device(aqueduct, admin.userId(), "Segundo", at("2026-10-02T10:00:00Z"));
    UUID third = device(aqueduct, admin.userId(), "Tercero", at("2026-10-03T10:00:00Z"));

    String firstPage =
        list(session, "?limit=2")
            .andExpect(jsonPath("$.items[0].id").value(third.toString()))
            .andExpect(jsonPath("$.items[1].id").value(second.toString()))
            .andExpect(jsonPath("$.next_cursor").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = firstPage.replaceAll(".*\"next_cursor\":\"([^\"]+)\".*", "$1");
    list(session, "?limit=2&cursor=" + cursor)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(first.toString()))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    String last = cursor.substring(cursor.length() - 1);
    String altered = cursor.substring(0, cursor.length() - 1) + ("A".equals(last) ? "B" : "A");
    list(session, "?limit=2&cursor=" + altered)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
    list(session, "?limit=0")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    list(session, "?limit=101")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void otherAqueductsSeeNothingAndOnlyTheManagersAreAllowed() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account admin = account("device.list.owner", "BOARD_ADMIN", aqueduct);
    UUID hidden = device(aqueduct, admin.userId(), "Oculto", at("2026-10-01T10:00:00Z"));
    Session team = login(account("device.list.team", "PROJECT_TEAM", aqueduct));
    Session operator = login(account("device.list.operator", "OPERATOR", aqueduct));
    Session stranger =
        login(account("device.list.stranger", "BOARD_ADMIN", newAqueductWithRules()));

    list(stranger, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.items[0].id").doesNotExist());
    list(team, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(hidden.toString()));
    list(operator, "").andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/devices")).andExpect(status().isUnauthorized());
  }
}
