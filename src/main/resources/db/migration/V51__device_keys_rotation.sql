-- V51: rotation of the device keys (docs/Protocolo-de-dispositivos.md, sections 1.2 and 1.3).
-- A device has at most one key without revoked_at. Rotation revokes the previous key first
-- (revoked_at in the future, DEVICE_KEY_ROTATION_DAYS) and only then inserts the new one, so the
-- index is never violated inside the transaction.
CREATE UNIQUE INDEX device_keys_one_active_uq ON devices.device_keys (device_id)
  WHERE revoked_at IS NULL;

-- The signature check finds the device before the aqueduct is known, so the owner may read the
-- devices and their keys across aqueducts. The application role keeps the tenant policy only.
CREATE POLICY device_lookup ON devices.devices
  FOR SELECT
  USING (audit.is_object_owner());

CREATE POLICY device_lookup ON devices.device_keys
  FOR SELECT
  USING (audit.is_object_owner());

-- Device and its valid keys for a device id: a key is valid while revoked_at is null or still in
-- the future. One row per valid key; a device without valid keys returns one row with no key.
CREATE FUNCTION devices.find_device_credentials(p_device_id uuid)
RETURNS TABLE (
  aqueduct_id uuid,
  status      varchar(12),
  kind        varchar(20),
  tank_id     uuid,
  valve_id    uuid,
  key_id      uuid,
  public_key  varchar(64)
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, devices
AS $$
  SELECT d.aqueduct_id, d.status, d.kind, d.tank_id, d.valve_id, k.id, k.public_key
    FROM devices.devices d
    LEFT JOIN devices.device_keys k
      ON k.device_id = d.id
     AND (k.revoked_at IS NULL OR k.revoked_at > now())
   WHERE d.id = p_device_id;
$$;

REVOKE ALL ON FUNCTION devices.find_device_credentials(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION devices.find_device_credentials(uuid) TO caudal_app;
