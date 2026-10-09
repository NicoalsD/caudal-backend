-- V20: ops.readings.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.readings (
  id                         uuid NOT NULL,
  aqueduct_id                uuid NOT NULL,
  tank_id                    uuid NOT NULL,
  gauge_value                numeric(6,2) NOT NULL,
  water_appearance_code      varchar(${catalog_code_max}) NOT NULL,
  damage_noticed             boolean NOT NULL DEFAULT false,
  note                       varchar(${note_max}),
  observed_at                timestamptz NOT NULL,
  received_at                timestamptz NOT NULL DEFAULT now(),
  source                     varchar(12) NOT NULL,
  recorded_by                uuid,
  device_id                  uuid,
  sync_batch_id              uuid,
  import_batch_id            uuid,
  rule_set_id                uuid NOT NULL,
  validation_status          varchar(20) NOT NULL,
  CONSTRAINT readings_pk PRIMARY KEY (id),
  CONSTRAINT readings_source_values CHECK (source IN ('MANUAL_APP', 'SENSOR', 'IMPORT')),
  CONSTRAINT readings_validation_status_values CHECK (validation_status IN ('ACCEPTED', 'FLAGGED')),
  CONSTRAINT readings_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.readings IS 'Lecturas del tanque (append-only). Estatus epistémico OBSERVED.';
COMMENT ON COLUMN ops.readings.id IS '[I] UUID generado por el cliente (idempotencia).';
COMMENT ON COLUMN ops.readings.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.readings.tank_id IS '[I] Tanque.';
COMMENT ON COLUMN ops.readings.gauge_value IS '[I] Valor leído en la regla.';
COMMENT ON COLUMN ops.readings.water_appearance_code IS '[I] Aspecto del agua (catálogo).';
COMMENT ON COLUMN ops.readings.damage_noticed IS '[I] Se notó algún daño.';
COMMENT ON COLUMN ops.readings.note IS '[I] Nota libre.';
COMMENT ON COLUMN ops.readings.observed_at IS '[I] Momento de la observación (reloj del celular).';
COMMENT ON COLUMN ops.readings.received_at IS '[I] Llegada al servidor.';
COMMENT ON COLUMN ops.readings.source IS '[I] Origen.';
COMMENT ON COLUMN ops.readings.recorded_by IS '[I] Usuario (nulo si viene de un sensor).';
COMMENT ON COLUMN ops.readings.device_id IS '[I] Dispositivo si aplica.';
COMMENT ON COLUMN ops.readings.sync_batch_id IS '[I] Lote offline si aplica.';
COMMENT ON COLUMN ops.readings.import_batch_id IS '[I] Importación si aplica.';
COMMENT ON COLUMN ops.readings.rule_set_id IS '[I] Reglas vigentes al validar.';
COMMENT ON COLUMN ops.readings.validation_status IS '[I] Resultado de la validación (los datos inválidos se rechazan con 422 y no se guardan).';

ALTER TABLE ops.readings ADD CONSTRAINT readings_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.readings ADD CONSTRAINT readings_tank_id_fk
  FOREIGN KEY (tank_id, aqueduct_id) REFERENCES org.tanks (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.readings ADD CONSTRAINT readings_recorded_by_fk
  FOREIGN KEY (recorded_by) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE ops.readings ADD CONSTRAINT readings_sync_batch_id_fk
  FOREIGN KEY (sync_batch_id, aqueduct_id) REFERENCES ops.sync_batches (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.readings ADD CONSTRAINT readings_rule_set_id_fk
  FOREIGN KEY (rule_set_id, aqueduct_id) REFERENCES org.rule_sets (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX readings_aqueduct_id_idx ON ops.readings (aqueduct_id);
CREATE INDEX readings_tank_id_idx ON ops.readings (tank_id);
CREATE INDEX readings_recorded_by_idx ON ops.readings (recorded_by);
CREATE INDEX readings_device_id_idx ON ops.readings (device_id);
CREATE INDEX readings_sync_batch_id_idx ON ops.readings (sync_batch_id);
CREATE INDEX readings_import_batch_id_idx ON ops.readings (import_batch_id);
CREATE INDEX readings_rule_set_id_idx ON ops.readings (rule_set_id);
CREATE INDEX readings_tank_time_idx ON ops.readings (aqueduct_id, tank_id, observed_at DESC);

REVOKE ALL ON ops.readings FROM PUBLIC;
GRANT SELECT, INSERT ON ops.readings TO caudal_app;
