-- V33: devices.device_nonces, devices.telemetry_points.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE devices.device_nonces (
  device_id                  uuid,
  nonce                      char(32),
  seen_at                    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT device_nonces_pk PRIMARY KEY (device_id, nonce)
);

COMMENT ON TABLE devices.device_nonces IS 'Nonces ya vistos (anti-repetición); se purgan según retention_policies.';
COMMENT ON COLUMN devices.device_nonces.device_id IS '[I] Dispositivo.';
COMMENT ON COLUMN devices.device_nonces.nonce IS '[I] Nonce de 128 bits en hex.';
COMMENT ON COLUMN devices.device_nonces.seen_at IS '[I] Momento.';

ALTER TABLE devices.device_nonces ADD CONSTRAINT device_nonces_device_id_fk
  FOREIGN KEY (device_id) REFERENCES devices.devices (id) ON DELETE RESTRICT;

CREATE INDEX device_nonces_seen_idx ON devices.device_nonces (seen_at);

REVOKE ALL ON devices.device_nonces FROM PUBLIC;
GRANT SELECT, INSERT ON devices.device_nonces TO caudal_app;

CREATE TABLE devices.telemetry_points (
  id                         uuid NOT NULL,
  aqueduct_id                uuid NOT NULL,
  device_id                  uuid NOT NULL,
  metric                     varchar(20) NOT NULL,
  value                      numeric(10,3) NOT NULL,
  observed_at                timestamptz NOT NULL,
  received_at                timestamptz NOT NULL DEFAULT now(),
  reading_id                 uuid,
  CONSTRAINT telemetry_points_pk PRIMARY KEY (id),
  CONSTRAINT telemetry_points_metric_values CHECK (metric IN ('LEVEL', 'BATTERY_V', 'RSSI', 'VALVE_POSITION')),
  CONSTRAINT telemetry_points_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE devices.telemetry_points IS 'Telemetría cruda de los dispositivos (idempotente por id del punto).';
COMMENT ON COLUMN devices.telemetry_points.id IS '[I] UUID generado por el dispositivo.';
COMMENT ON COLUMN devices.telemetry_points.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN devices.telemetry_points.device_id IS '[I] Dispositivo.';
COMMENT ON COLUMN devices.telemetry_points.metric IS '[I] Métrica.';
COMMENT ON COLUMN devices.telemetry_points.value IS '[I] Valor.';
COMMENT ON COLUMN devices.telemetry_points.observed_at IS '[I] Momento de la medición.';
COMMENT ON COLUMN devices.telemetry_points.received_at IS '[I] Llegada.';
COMMENT ON COLUMN devices.telemetry_points.reading_id IS '[I] Lectura generada a partir de este punto.';

ALTER TABLE devices.telemetry_points ADD CONSTRAINT telemetry_points_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE devices.telemetry_points ADD CONSTRAINT telemetry_points_device_id_fk
  FOREIGN KEY (device_id, aqueduct_id) REFERENCES devices.devices (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE devices.telemetry_points ADD CONSTRAINT telemetry_points_reading_id_fk
  FOREIGN KEY (reading_id, aqueduct_id) REFERENCES ops.readings (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX telemetry_points_aqueduct_id_idx ON devices.telemetry_points (aqueduct_id);
CREATE INDEX telemetry_points_device_id_idx ON devices.telemetry_points (device_id);
CREATE INDEX telemetry_points_reading_id_idx ON devices.telemetry_points (reading_id);
CREATE INDEX telemetry_points_device_time_idx ON devices.telemetry_points (device_id, observed_at DESC);

REVOKE ALL ON devices.telemetry_points FROM PUBLIC;
GRANT SELECT, INSERT ON devices.telemetry_points TO caudal_app;
