-- V25: ops.schedule_proposals.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.schedule_proposals (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  service_date               date NOT NULL,
  rule_set_id                uuid NOT NULL,
  status                     varchar(25) NOT NULL DEFAULT 'DRAFT',
  tank_level_snapshot        numeric(6,2) NOT NULL,
  tank_band                  varchar(10) NOT NULL,
  available_hours            numeric(4,2) NOT NULL,
  forecast_run_id            uuid,
  unserved_sectors           jsonb NOT NULL DEFAULT '[]',
  strategy_code              varchar(40) NOT NULL,
  generated_by               uuid,
  generated_at               timestamptz NOT NULL DEFAULT now(),
  lock_version               integer NOT NULL DEFAULT 0,
  CONSTRAINT schedule_proposals_pk PRIMARY KEY (id),
  CONSTRAINT schedule_proposals_status_values CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'APPROVED_WITH_CHANGES', 'REJECTED', 'PUBLISHED', 'CLOSED')),
  CONSTRAINT schedule_proposals_tank_band_values CHECK (tank_band IN ('HIGH', 'LOW', 'CRITICAL')),
  CONSTRAINT schedule_proposals_available_hours_range CHECK (available_hours >= 0),
  CONSTRAINT schedule_proposals_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.schedule_proposals IS 'Propuesta de turnos de un día (máquina de estados).';
COMMENT ON COLUMN ops.schedule_proposals.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.schedule_proposals.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.schedule_proposals.service_date IS '[P] Día de servicio.';
COMMENT ON COLUMN ops.schedule_proposals.rule_set_id IS '[I] Reglas usadas.';
COMMENT ON COLUMN ops.schedule_proposals.status IS '[I] Estado.';
COMMENT ON COLUMN ops.schedule_proposals.tank_level_snapshot IS '[I] Nivel usado.';
COMMENT ON COLUMN ops.schedule_proposals.tank_band IS '[I] Banda.';
COMMENT ON COLUMN ops.schedule_proposals.available_hours IS '[I] Horas disponibles (ESTIMATED).';
COMMENT ON COLUMN ops.schedule_proposals.forecast_run_id IS '[I] Pronóstico considerado.';
COMMENT ON COLUMN ops.schedule_proposals.unserved_sectors IS '[P] Sectores sin turno y su motivo (código y parámetros).';
COMMENT ON COLUMN ops.schedule_proposals.strategy_code IS '[I] Estrategia usada.';
COMMENT ON COLUMN ops.schedule_proposals.generated_by IS '[I] Quién la generó.';
COMMENT ON COLUMN ops.schedule_proposals.generated_at IS '[I] Momento.';
COMMENT ON COLUMN ops.schedule_proposals.lock_version IS '[I] Bloqueo optimista.';

ALTER TABLE ops.schedule_proposals ADD CONSTRAINT schedule_proposals_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_proposals ADD CONSTRAINT schedule_proposals_rule_set_id_fk
  FOREIGN KEY (rule_set_id, aqueduct_id) REFERENCES org.rule_sets (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_proposals ADD CONSTRAINT schedule_proposals_forecast_run_id_fk
  FOREIGN KEY (forecast_run_id, aqueduct_id) REFERENCES ops.forecast_runs (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_proposals ADD CONSTRAINT schedule_proposals_generated_by_fk
  FOREIGN KEY (generated_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX schedule_proposals_aqueduct_id_idx ON ops.schedule_proposals (aqueduct_id);
CREATE INDEX schedule_proposals_rule_set_id_idx ON ops.schedule_proposals (rule_set_id);
CREATE INDEX schedule_proposals_forecast_run_id_idx ON ops.schedule_proposals (forecast_run_id);
CREATE INDEX schedule_proposals_generated_by_idx ON ops.schedule_proposals (generated_by);
CREATE INDEX schedule_proposals_date_idx ON ops.schedule_proposals (aqueduct_id, service_date DESC);

REVOKE ALL ON ops.schedule_proposals FROM PUBLIC;
GRANT SELECT, INSERT ON ops.schedule_proposals TO caudal_app;
GRANT UPDATE (status, lock_version) ON ops.schedule_proposals TO caudal_app;
