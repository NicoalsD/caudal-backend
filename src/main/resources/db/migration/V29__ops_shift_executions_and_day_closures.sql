-- V29: ops.shift_executions, ops.day_closures.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.shift_executions (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  schedule_item_id           uuid NOT NULL,
  status                     varchar(15) NOT NULL,
  actual_start               timestamptz,
  actual_end                 timestamptz,
  note                       varchar(${note_max}),
  supersedes_id              uuid,
  import_batch_id            uuid,
  recorded_by                uuid NOT NULL,
  recorded_at                timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT shift_executions_pk PRIMARY KEY (id),
  CONSTRAINT shift_executions_status_values CHECK (status IN ('COMPLETED', 'PARTIAL', 'NOT_EXECUTED')),
  CONSTRAINT shift_executions_actual_end_rule CHECK (actual_end > actual_start),
  CONSTRAINT shift_executions_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.shift_executions IS 'Lo que pasó realmente en cada turno (CONFIRMED). Se reemplaza con supersedes_id.';
COMMENT ON COLUMN ops.shift_executions.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.shift_executions.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.shift_executions.schedule_item_id IS '[I] Turno.';
COMMENT ON COLUMN ops.shift_executions.status IS '[I] Resultado.';
COMMENT ON COLUMN ops.shift_executions.actual_start IS '[I] Inicio real.';
COMMENT ON COLUMN ops.shift_executions.actual_end IS '[I] Fin real.';
COMMENT ON COLUMN ops.shift_executions.note IS '[I] Novedades.';
COMMENT ON COLUMN ops.shift_executions.supersedes_id IS '[I] Registro que reemplaza.';
COMMENT ON COLUMN ops.shift_executions.import_batch_id IS '[I] Importación si aplica.';
COMMENT ON COLUMN ops.shift_executions.recorded_by IS '[I] Quién registró.';
COMMENT ON COLUMN ops.shift_executions.recorded_at IS '[I] Momento.';

ALTER TABLE ops.shift_executions ADD CONSTRAINT shift_executions_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.shift_executions ADD CONSTRAINT shift_executions_schedule_item_id_fk
  FOREIGN KEY (schedule_item_id, aqueduct_id) REFERENCES ops.schedule_items (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.shift_executions ADD CONSTRAINT shift_executions_supersedes_id_fk
  FOREIGN KEY (supersedes_id, aqueduct_id) REFERENCES ops.shift_executions (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.shift_executions ADD CONSTRAINT shift_executions_recorded_by_fk
  FOREIGN KEY (recorded_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX shift_executions_aqueduct_id_idx ON ops.shift_executions (aqueduct_id);
CREATE INDEX shift_executions_schedule_item_id_idx ON ops.shift_executions (schedule_item_id);
CREATE INDEX shift_executions_supersedes_id_idx ON ops.shift_executions (supersedes_id);
CREATE INDEX shift_executions_import_batch_id_idx ON ops.shift_executions (import_batch_id);
CREATE INDEX shift_executions_recorded_by_idx ON ops.shift_executions (recorded_by);

REVOKE ALL ON ops.shift_executions FROM PUBLIC;
GRANT SELECT, INSERT ON ops.shift_executions TO caudal_app;

CREATE TABLE ops.day_closures (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  service_date               date NOT NULL,
  notes                      varchar(${note_max}),
  closed_by                  uuid NOT NULL,
  closed_at                  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT day_closures_pk PRIMARY KEY (id),
  CONSTRAINT day_closures_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.day_closures IS 'Cierre del día de servicio.';
COMMENT ON COLUMN ops.day_closures.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.day_closures.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.day_closures.service_date IS '[I] Día (único por acueducto).';
COMMENT ON COLUMN ops.day_closures.notes IS '[I] Novedades del día.';
COMMENT ON COLUMN ops.day_closures.closed_by IS '[I] Quién cerró.';
COMMENT ON COLUMN ops.day_closures.closed_at IS '[I] Momento.';

ALTER TABLE ops.day_closures ADD CONSTRAINT day_closures_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.day_closures ADD CONSTRAINT day_closures_closed_by_fk
  FOREIGN KEY (closed_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX day_closures_aqueduct_id_idx ON ops.day_closures (aqueduct_id);
CREATE INDEX day_closures_closed_by_idx ON ops.day_closures (closed_by);
ALTER TABLE ops.day_closures ADD CONSTRAINT day_closures_day_uq UNIQUE (aqueduct_id, service_date);

REVOKE ALL ON ops.day_closures FROM PUBLIC;
GRANT SELECT, INSERT ON ops.day_closures TO caudal_app;
