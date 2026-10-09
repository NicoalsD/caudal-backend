-- V21: ops.reading_issues, ops.reading_corrections.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.reading_issues (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  reading_id                 uuid NOT NULL,
  issue_code                 varchar(30) NOT NULL,
  severity                   varchar(10) NOT NULL,
  details                    jsonb NOT NULL DEFAULT '{}',
  CONSTRAINT reading_issues_pk PRIMARY KEY (id),
  CONSTRAINT reading_issues_issue_code_values CHECK (issue_code IN ('DUPLICATE', 'SUDDEN_JUMP')),
  CONSTRAINT reading_issues_severity_values CHECK (severity IN ('INFO', 'WARNING')),
  CONSTRAINT reading_issues_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.reading_issues IS 'Problemas que detectó la cadena de validación.';
COMMENT ON COLUMN ops.reading_issues.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.reading_issues.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.reading_issues.reading_id IS '[I] Lectura.';
COMMENT ON COLUMN ops.reading_issues.issue_code IS '[I] Código de la marca.';
COMMENT ON COLUMN ops.reading_issues.severity IS '[I] Severidad.';
COMMENT ON COLUMN ops.reading_issues.details IS '[I] Parámetros (valor, rango, ventana).';

ALTER TABLE ops.reading_issues ADD CONSTRAINT reading_issues_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.reading_issues ADD CONSTRAINT reading_issues_reading_id_fk
  FOREIGN KEY (reading_id, aqueduct_id) REFERENCES ops.readings (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX reading_issues_aqueduct_id_idx ON ops.reading_issues (aqueduct_id);
CREATE INDEX reading_issues_reading_id_idx ON ops.reading_issues (reading_id);

REVOKE ALL ON ops.reading_issues FROM PUBLIC;
GRANT SELECT, INSERT ON ops.reading_issues TO caudal_app;

CREATE TABLE ops.reading_corrections (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  reading_id                 uuid NOT NULL,
  corrected_gauge_value      numeric(6,2) NOT NULL,
  reason                     varchar(${reason_max}) NOT NULL,
  corrected_by               uuid NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT reading_corrections_pk PRIMARY KEY (id),
  CONSTRAINT reading_corrections_reason_length CHECK (char_length(reason) >= ${reason_min}),
  CONSTRAINT reading_corrections_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.reading_corrections IS 'Correcciones sin borrar el dato original (la vista effective_readings usa la última).';
COMMENT ON COLUMN ops.reading_corrections.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.reading_corrections.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.reading_corrections.reading_id IS '[I] Lectura corregida.';
COMMENT ON COLUMN ops.reading_corrections.corrected_gauge_value IS '[I] Valor corregido.';
COMMENT ON COLUMN ops.reading_corrections.reason IS '[I] Motivo.';
COMMENT ON COLUMN ops.reading_corrections.corrected_by IS '[I] Quién corrigió.';
COMMENT ON COLUMN ops.reading_corrections.created_at IS '[I] Momento.';

ALTER TABLE ops.reading_corrections ADD CONSTRAINT reading_corrections_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.reading_corrections ADD CONSTRAINT reading_corrections_reading_id_fk
  FOREIGN KEY (reading_id, aqueduct_id) REFERENCES ops.readings (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.reading_corrections ADD CONSTRAINT reading_corrections_corrected_by_fk
  FOREIGN KEY (corrected_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX reading_corrections_aqueduct_id_idx ON ops.reading_corrections (aqueduct_id);
CREATE INDEX reading_corrections_reading_id_idx ON ops.reading_corrections (reading_id);
CREATE INDEX reading_corrections_corrected_by_idx ON ops.reading_corrections (corrected_by);
CREATE INDEX reading_corrections_latest_idx ON ops.reading_corrections (reading_id, created_at DESC, id DESC);

REVOKE ALL ON ops.reading_corrections FROM PUBLIC;
GRANT SELECT, INSERT ON ops.reading_corrections TO caudal_app;

-- Effective value of each reading: the latest correction wins, the original row never changes.
-- security_invoker makes the view obey the RLS policies of the underlying tables.
CREATE VIEW ops.effective_readings WITH (security_invoker = true) AS
SELECT r.id,
       r.aqueduct_id,
       r.tank_id,
       r.gauge_value AS observed_gauge_value,
       COALESCE(c.corrected_gauge_value, r.gauge_value) AS gauge_value,
       c.id AS correction_id,
       r.water_appearance_code,
       r.damage_noticed,
       r.observed_at,
       r.received_at,
       r.source,
       r.validation_status,
       r.rule_set_id
  FROM ops.readings r
  LEFT JOIN LATERAL (
        SELECT rc.id, rc.corrected_gauge_value
          FROM ops.reading_corrections rc
         WHERE rc.reading_id = r.id
         ORDER BY rc.created_at DESC, rc.id DESC
         LIMIT 1) c ON true;

COMMENT ON VIEW ops.effective_readings IS 'Lecturas con su valor efectivo: la última corrección o el valor observado.';
REVOKE ALL ON ops.effective_readings FROM PUBLIC;
GRANT SELECT ON ops.effective_readings TO caudal_app;
