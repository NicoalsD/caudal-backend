-- V38: sim.import_batches.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE sim.import_batches (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  kind                       varchar(20) NOT NULL,
  scenario_name              varchar(60) NOT NULL,
  seed                       bigint NOT NULL,
  simulator_version          varchar(30) NOT NULL,
  rows_received              integer NOT NULL,
  rows_accepted              integer NOT NULL,
  rows_rejected              integer NOT NULL,
  file_sha256                char(64) NOT NULL,
  imported_by                uuid NOT NULL,
  imported_at                timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT import_batches_pk PRIMARY KEY (id),
  CONSTRAINT import_batches_kind_values CHECK (kind IN ('READINGS', 'SHIFT_EXECUTIONS', 'INCIDENTS')),
  CONSTRAINT import_batches_rows_received_range CHECK (rows_received >= 0),
  CONSTRAINT import_batches_rows_accepted_range CHECK (rows_accepted >= 0),
  CONSTRAINT import_batches_rows_rejected_range CHECK (rows_rejected >= 0),
  CONSTRAINT import_batches_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE sim.import_batches IS 'Importaciones de historial simulado (solo acueductos con is_demo = true).';
COMMENT ON COLUMN sim.import_batches.id IS '[I] Identificador.';
COMMENT ON COLUMN sim.import_batches.aqueduct_id IS '[I] Acueducto demo (trigger valida is_demo).';
COMMENT ON COLUMN sim.import_batches.kind IS '[I] Qué se importó.';
COMMENT ON COLUMN sim.import_batches.scenario_name IS '[I] Escenario del simulador.';
COMMENT ON COLUMN sim.import_batches.seed IS '[I] Semilla.';
COMMENT ON COLUMN sim.import_batches.simulator_version IS '[I] Versión del simulador.';
COMMENT ON COLUMN sim.import_batches.rows_received IS '[I] Filas recibidas.';
COMMENT ON COLUMN sim.import_batches.rows_accepted IS '[I] Aceptadas.';
COMMENT ON COLUMN sim.import_batches.rows_rejected IS '[I] Rechazadas.';
COMMENT ON COLUMN sim.import_batches.file_sha256 IS '[I] Hash del archivo.';
COMMENT ON COLUMN sim.import_batches.imported_by IS '[I] Usuario PROJECT_TEAM.';
COMMENT ON COLUMN sim.import_batches.imported_at IS '[I] Momento.';

ALTER TABLE sim.import_batches ADD CONSTRAINT import_batches_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE sim.import_batches ADD CONSTRAINT import_batches_imported_by_fk
  FOREIGN KEY (imported_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX import_batches_aqueduct_id_idx ON sim.import_batches (aqueduct_id);
CREATE INDEX import_batches_imported_by_idx ON sim.import_batches (imported_by);

REVOKE ALL ON sim.import_batches FROM PUBLIC;
GRANT SELECT, INSERT ON sim.import_batches TO caudal_app;

-- Foreign keys that waited for sim.import_batches.
ALTER TABLE ops.readings ADD CONSTRAINT readings_import_batch_id_fk
  FOREIGN KEY (import_batch_id, aqueduct_id) REFERENCES sim.import_batches (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.shift_executions ADD CONSTRAINT shift_executions_import_batch_id_fk
  FOREIGN KEY (import_batch_id, aqueduct_id) REFERENCES sim.import_batches (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.incidents ADD CONSTRAINT incidents_import_batch_id_fk
  FOREIGN KEY (import_batch_id, aqueduct_id) REFERENCES sim.import_batches (id, aqueduct_id) ON DELETE RESTRICT;

-- Simulated history can only be imported into a demo aqueduct (DB doc, section 8).
CREATE FUNCTION sim.guard_demo_only()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, org, sim
AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM org.aqueducts a WHERE a.id = NEW.aqueduct_id AND a.is_demo) THEN
    RAISE EXCEPTION 'demo_only'
      USING ERRCODE = 'check_violation',
            HINT = 'La importación de historial solo aplica a acueductos con is_demo = true.';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER import_batches_demo_only
  BEFORE INSERT ON sim.import_batches
  FOR EACH ROW EXECUTE FUNCTION sim.guard_demo_only();
