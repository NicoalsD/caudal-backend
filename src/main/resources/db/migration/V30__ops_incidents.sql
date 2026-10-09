-- V30: ops.incidents, ops.incident_status_history.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.incidents (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  source                     varchar(15) NOT NULL,
  category_code              varchar(${catalog_code_max}) NOT NULL,
  sector_id                  uuid,
  description                varchar(${incident_description_max}) NOT NULL,
  location_hint              varchar(${location_hint_max}),
  status                     varchar(12) NOT NULL DEFAULT 'REPORTED',
  tracking_code_hash         char(64) NOT NULL,
  reporter_ip_hmac           varchar(64),
  import_batch_id            uuid,
  reported_at                timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT incidents_pk PRIMARY KEY (id),
  CONSTRAINT incidents_source_values CHECK (source IN ('PUBLIC_FORM', 'OPERATOR', 'ANOMALY', 'IMPORT')),
  CONSTRAINT incidents_description_length CHECK (char_length(description) >= ${incident_description_min}),
  CONSTRAINT incidents_status_values CHECK (status IN ('REPORTED', 'VERIFYING', 'CONFIRMED', 'RESOLVED', 'DISMISSED')),
  CONSTRAINT incidents_tracking_code_hash_uq UNIQUE (tracking_code_hash),
  CONSTRAINT incidents_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.incidents IS 'Reportes de daño (públicos, del fontanero o derivados de anomalías). Sin datos personales.';
COMMENT ON COLUMN ops.incidents.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.incidents.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.incidents.source IS '[I] Origen.';
COMMENT ON COLUMN ops.incidents.category_code IS '[P] Categoría (catálogo).';
COMMENT ON COLUMN ops.incidents.sector_id IS '[P] Sector si se conoce.';
COMMENT ON COLUMN ops.incidents.description IS '[I] Descripción.';
COMMENT ON COLUMN ops.incidents.location_hint IS '[I] Referencia.';
COMMENT ON COLUMN ops.incidents.status IS '[P] Estado.';
COMMENT ON COLUMN ops.incidents.tracking_code_hash IS '[S] SHA-256 del código de seguimiento.';
COMMENT ON COLUMN ops.incidents.reporter_ip_hmac IS '[C] HMAC de la IP (control de abuso).';
COMMENT ON COLUMN ops.incidents.import_batch_id IS '[I] Importación si aplica.';
COMMENT ON COLUMN ops.incidents.reported_at IS '[I] Momento.';

ALTER TABLE ops.incidents ADD CONSTRAINT incidents_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.incidents ADD CONSTRAINT incidents_sector_id_fk
  FOREIGN KEY (sector_id, aqueduct_id) REFERENCES org.sectors (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX incidents_aqueduct_id_idx ON ops.incidents (aqueduct_id);
CREATE INDEX incidents_sector_id_idx ON ops.incidents (sector_id);
CREATE INDEX incidents_import_batch_id_idx ON ops.incidents (import_batch_id);
CREATE INDEX incidents_status_idx ON ops.incidents (aqueduct_id, status, reported_at DESC);

REVOKE ALL ON ops.incidents FROM PUBLIC;
GRANT SELECT, INSERT ON ops.incidents TO caudal_app;
GRANT UPDATE (status) ON ops.incidents TO caudal_app;

-- Foreign keys that waited for ops.incidents.
ALTER TABLE ops.anomalies ADD CONSTRAINT anomalies_related_incident_id_fk
  FOREIGN KEY (related_incident_id, aqueduct_id) REFERENCES ops.incidents (id, aqueduct_id) ON DELETE RESTRICT;

CREATE TABLE ops.incident_status_history (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  incident_id                uuid NOT NULL,
  from_status                varchar(12),
  to_status                  varchar(12) NOT NULL,
  note                       varchar(${note_max}),
  changed_by                 uuid,
  changed_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT incident_status_history_pk PRIMARY KEY (id),
  CONSTRAINT incident_status_history_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.incident_status_history IS 'Historial de estados de un incidente.';
COMMENT ON COLUMN ops.incident_status_history.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.incident_status_history.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.incident_status_history.incident_id IS '[I] Incidente.';
COMMENT ON COLUMN ops.incident_status_history.from_status IS '[I] Estado anterior.';
COMMENT ON COLUMN ops.incident_status_history.to_status IS '[I] Estado nuevo.';
COMMENT ON COLUMN ops.incident_status_history.note IS '[I] Nota.';
COMMENT ON COLUMN ops.incident_status_history.changed_by IS '[I] Quién lo cambió.';
COMMENT ON COLUMN ops.incident_status_history.changed_at IS '[I] Momento.';

ALTER TABLE ops.incident_status_history ADD CONSTRAINT incident_status_history_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.incident_status_history ADD CONSTRAINT incident_status_history_incident_id_fk
  FOREIGN KEY (incident_id, aqueduct_id) REFERENCES ops.incidents (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.incident_status_history ADD CONSTRAINT incident_status_history_changed_by_fk
  FOREIGN KEY (changed_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX incident_status_history_aqueduct_id_idx ON ops.incident_status_history (aqueduct_id);
CREATE INDEX incident_status_history_incident_id_idx ON ops.incident_status_history (incident_id);
CREATE INDEX incident_status_history_changed_by_idx ON ops.incident_status_history (changed_by);

REVOKE ALL ON ops.incident_status_history FROM PUBLIC;
GRANT SELECT, INSERT ON ops.incident_status_history TO caudal_app;
