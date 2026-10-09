-- V32: devices.devices, devices.device_keys.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE devices.devices (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  kind                       varchar(20) NOT NULL,
  name                       varchar(${place_name_max}) NOT NULL,
  tank_id                    uuid,
  valve_id                   uuid,
  status                     varchar(12) NOT NULL DEFAULT 'ACTIVE',
  firmware_version           varchar(30),
  last_seen_at               timestamptz,
  created_by                 uuid NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT devices_pk PRIMARY KEY (id),
  CONSTRAINT devices_kind_values CHECK (kind IN ('LEVEL_SENSOR', 'VALVE_ACTUATOR', 'GATEWAY')),
  CONSTRAINT devices_name_length CHECK (char_length(name) >= ${place_name_min}),
  CONSTRAINT devices_status_values CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
  CONSTRAINT devices_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE devices.devices IS 'Sensores de nivel, actuadores de válvula y gateways.';
COMMENT ON COLUMN devices.devices.id IS '[I] Identificador.';
COMMENT ON COLUMN devices.devices.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN devices.devices.kind IS '[I] Tipo.';
COMMENT ON COLUMN devices.devices.name IS '[I] Nombre.';
COMMENT ON COLUMN devices.devices.tank_id IS '[I] Tanque (sensores).';
COMMENT ON COLUMN devices.devices.valve_id IS '[I] Válvula (actuadores).';
COMMENT ON COLUMN devices.devices.status IS '[I] Estado.';
COMMENT ON COLUMN devices.devices.firmware_version IS '[I] Versión del firmware o del simulador.';
COMMENT ON COLUMN devices.devices.last_seen_at IS '[I] Última conexión.';
COMMENT ON COLUMN devices.devices.created_by IS '[I] Quién lo registró.';
COMMENT ON COLUMN devices.devices.created_at IS '[I] Creación.';

ALTER TABLE devices.devices ADD CONSTRAINT devices_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE devices.devices ADD CONSTRAINT devices_tank_id_fk
  FOREIGN KEY (tank_id, aqueduct_id) REFERENCES org.tanks (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE devices.devices ADD CONSTRAINT devices_valve_id_fk
  FOREIGN KEY (valve_id) REFERENCES org.valves (id) ON DELETE RESTRICT;
ALTER TABLE devices.devices ADD CONSTRAINT devices_created_by_fk
  FOREIGN KEY (created_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX devices_aqueduct_id_idx ON devices.devices (aqueduct_id);
CREATE INDEX devices_tank_id_idx ON devices.devices (tank_id);
CREATE INDEX devices_valve_id_idx ON devices.devices (valve_id);
CREATE INDEX devices_created_by_idx ON devices.devices (created_by);

REVOKE ALL ON devices.devices FROM PUBLIC;
GRANT SELECT, INSERT ON devices.devices TO caudal_app;
GRANT UPDATE (name, status, firmware_version, last_seen_at) ON devices.devices TO caudal_app;

-- Foreign keys that waited for devices.devices.
ALTER TABLE iam.security_events ADD CONSTRAINT security_events_device_id_fk
  FOREIGN KEY (device_id) REFERENCES devices.devices (id) ON DELETE RESTRICT;
ALTER TABLE ops.readings ADD CONSTRAINT readings_device_id_fk
  FOREIGN KEY (device_id, aqueduct_id) REFERENCES devices.devices (id, aqueduct_id) ON DELETE RESTRICT;

CREATE TABLE devices.device_keys (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  device_id                  uuid NOT NULL,
  public_key                 varchar(64) NOT NULL,
  fingerprint                char(64) NOT NULL,
  activated_at               timestamptz NOT NULL DEFAULT now(),
  revoked_at                 timestamptz,
  CONSTRAINT device_keys_pk PRIMARY KEY (id),
  CONSTRAINT device_keys_public_key_uq UNIQUE (public_key),
  CONSTRAINT device_keys_fingerprint_uq UNIQUE (fingerprint),
  CONSTRAINT device_keys_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE devices.device_keys IS 'Claves públicas Ed25519 con rotación (la privada nunca llega al servidor).';
COMMENT ON COLUMN devices.device_keys.id IS '[I] Identificador.';
COMMENT ON COLUMN devices.device_keys.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN devices.device_keys.device_id IS '[I] Dispositivo.';
COMMENT ON COLUMN devices.device_keys.public_key IS '[I] Clave pública Ed25519 en base64url (32 bytes).';
COMMENT ON COLUMN devices.device_keys.fingerprint IS '[I] SHA-256 de la clave.';
COMMENT ON COLUMN devices.device_keys.activated_at IS '[I] Activación.';
COMMENT ON COLUMN devices.device_keys.revoked_at IS '[I] Revocación.';

ALTER TABLE devices.device_keys ADD CONSTRAINT device_keys_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE devices.device_keys ADD CONSTRAINT device_keys_device_id_fk
  FOREIGN KEY (device_id, aqueduct_id) REFERENCES devices.devices (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX device_keys_aqueduct_id_idx ON devices.device_keys (aqueduct_id);
CREATE INDEX device_keys_device_id_idx ON devices.device_keys (device_id);

REVOKE ALL ON devices.device_keys FROM PUBLIC;
GRANT SELECT, INSERT ON devices.device_keys TO caudal_app;
GRANT UPDATE (revoked_at) ON devices.device_keys TO caudal_app;
