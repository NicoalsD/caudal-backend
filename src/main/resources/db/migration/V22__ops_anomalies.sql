-- V22: ops.anomalies.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.anomalies (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  kind                       varchar(30) NOT NULL,
  detected_at                timestamptz NOT NULL DEFAULT now(),
  evidence                   jsonb NOT NULL DEFAULT '{}',
  status                     varchar(12) NOT NULL DEFAULT 'OPEN',
  resolved_by                uuid,
  resolved_at                timestamptz,
  resolution_note            varchar(${note_max}),
  related_incident_id        uuid,
  CONSTRAINT anomalies_pk PRIMARY KEY (id),
  CONSTRAINT anomalies_kind_values CHECK (kind IN ('POSSIBLE_LEAK', 'SENSOR_FAULT', 'STALE_DATA', 'SUSPECTED_DUPLICATE', 'ABNORMAL_DROP')),
  CONSTRAINT anomalies_status_values CHECK (status IN ('OPEN', 'CONFIRMED', 'DISMISSED')),
  CONSTRAINT anomalies_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.anomalies IS 'Sospechas inferidas por reglas (estatus INFERRED hasta confirmarse).';
COMMENT ON COLUMN ops.anomalies.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.anomalies.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.anomalies.kind IS '[I] Tipo.';
COMMENT ON COLUMN ops.anomalies.detected_at IS '[I] Detección.';
COMMENT ON COLUMN ops.anomalies.evidence IS '[I] Evidencia (lecturas, pendiente).';
COMMENT ON COLUMN ops.anomalies.status IS '[I] Estado.';
COMMENT ON COLUMN ops.anomalies.resolved_by IS '[I] Quién resolvió.';
COMMENT ON COLUMN ops.anomalies.resolved_at IS '[I] Resolución.';
COMMENT ON COLUMN ops.anomalies.resolution_note IS '[I] Nota.';
COMMENT ON COLUMN ops.anomalies.related_incident_id IS '[I] Incidente relacionado.';

ALTER TABLE ops.anomalies ADD CONSTRAINT anomalies_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.anomalies ADD CONSTRAINT anomalies_resolved_by_fk
  FOREIGN KEY (resolved_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX anomalies_aqueduct_id_idx ON ops.anomalies (aqueduct_id);
CREATE INDEX anomalies_resolved_by_idx ON ops.anomalies (resolved_by);
CREATE INDEX anomalies_related_incident_id_idx ON ops.anomalies (related_incident_id);
CREATE INDEX anomalies_status_idx ON ops.anomalies (aqueduct_id, status, detected_at DESC);

REVOKE ALL ON ops.anomalies FROM PUBLIC;
GRANT SELECT, INSERT ON ops.anomalies TO caudal_app;
GRANT UPDATE (status, resolved_by, resolved_at, resolution_note, related_incident_id) ON ops.anomalies TO caudal_app;
