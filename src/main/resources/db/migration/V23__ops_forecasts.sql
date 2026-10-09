-- V23: ops.forecast_runs, ops.forecast_points.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.forecast_runs (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  tank_id                    uuid NOT NULL,
  model_name                 varchar(60) NOT NULL,
  model_version              varchar(40) NOT NULL,
  is_fallback                boolean NOT NULL,
  fallback_reason            varchar(30),
  horizon_days               smallint NOT NULL,
  context_from               timestamptz NOT NULL,
  context_to                 timestamptz NOT NULL,
  input_points               integer NOT NULL,
  latency_ms                 integer,
  rule_set_id                uuid NOT NULL,
  requested_by               uuid,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT forecast_runs_pk PRIMARY KEY (id),
  CONSTRAINT forecast_runs_fallback_reason_values CHECK (fallback_reason IN ('IA_TIMEOUT', 'CIRCUIT_OPEN', 'IA_ERROR', 'INSUFFICIENT_HISTORY', 'SHADOW_BASELINE')),
  CONSTRAINT forecast_runs_horizon_days_range CHECK (horizon_days > 0),
  CONSTRAINT forecast_runs_input_points_range CHECK (input_points >= 0),
  CONSTRAINT forecast_runs_latency_ms_range CHECK (latency_ms >= 0),
  CONSTRAINT forecast_runs_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.forecast_runs IS 'Cada pronóstico calculado (IA o estimación simple). Estatus ESTIMATED.';
COMMENT ON COLUMN ops.forecast_runs.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.forecast_runs.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.forecast_runs.tank_id IS '[I] Tanque.';
COMMENT ON COLUMN ops.forecast_runs.model_name IS '[I] Modelo (chronos-2-small, naive-persistence).';
COMMENT ON COLUMN ops.forecast_runs.model_version IS '[I] Versión.';
COMMENT ON COLUMN ops.forecast_runs.is_fallback IS '[I] Fue la estimación simple de respaldo.';
COMMENT ON COLUMN ops.forecast_runs.fallback_reason IS '[I] Motivo del respaldo.';
COMMENT ON COLUMN ops.forecast_runs.horizon_days IS '[I] Horizonte.';
COMMENT ON COLUMN ops.forecast_runs.context_from IS '[I] Inicio de la historia usada.';
COMMENT ON COLUMN ops.forecast_runs.context_to IS '[I] Fin de la historia usada.';
COMMENT ON COLUMN ops.forecast_runs.input_points IS '[I] Puntos enviados.';
COMMENT ON COLUMN ops.forecast_runs.latency_ms IS '[I] Latencia.';
COMMENT ON COLUMN ops.forecast_runs.rule_set_id IS '[I] Reglas vigentes.';
COMMENT ON COLUMN ops.forecast_runs.requested_by IS '[I] Usuario o nulo si fue el sistema.';
COMMENT ON COLUMN ops.forecast_runs.created_at IS '[I] Momento.';

ALTER TABLE ops.forecast_runs ADD CONSTRAINT forecast_runs_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_runs ADD CONSTRAINT forecast_runs_tank_id_fk
  FOREIGN KEY (tank_id, aqueduct_id) REFERENCES org.tanks (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_runs ADD CONSTRAINT forecast_runs_rule_set_id_fk
  FOREIGN KEY (rule_set_id, aqueduct_id) REFERENCES org.rule_sets (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_runs ADD CONSTRAINT forecast_runs_requested_by_fk
  FOREIGN KEY (requested_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX forecast_runs_aqueduct_id_idx ON ops.forecast_runs (aqueduct_id);
CREATE INDEX forecast_runs_tank_id_idx ON ops.forecast_runs (tank_id);
CREATE INDEX forecast_runs_rule_set_id_idx ON ops.forecast_runs (rule_set_id);
CREATE INDEX forecast_runs_requested_by_idx ON ops.forecast_runs (requested_by);
CREATE INDEX forecast_runs_tank_time_idx ON ops.forecast_runs (aqueduct_id, tank_id, created_at DESC);

REVOKE ALL ON ops.forecast_runs FROM PUBLIC;
GRANT SELECT, INSERT ON ops.forecast_runs TO caudal_app;

CREATE TABLE ops.forecast_points (
  forecast_run_id            uuid,
  target_date                date,
  aqueduct_id                uuid NOT NULL,
  p10                        numeric(6,2) NOT NULL,
  p50                        numeric(6,2) NOT NULL,
  p90                        numeric(6,2) NOT NULL,
  CONSTRAINT forecast_points_pk PRIMARY KEY (forecast_run_id, target_date),
  CONSTRAINT forecast_points_p50_rule CHECK (p50 >= p10),
  CONSTRAINT forecast_points_p90_rule CHECK (p90 >= p50)
);

COMMENT ON TABLE ops.forecast_points IS 'Cuantiles p10, p50 y p90 por día pronosticado.';
COMMENT ON COLUMN ops.forecast_points.forecast_run_id IS '[I] Pronóstico.';
COMMENT ON COLUMN ops.forecast_points.target_date IS '[I] Día pronosticado.';
COMMENT ON COLUMN ops.forecast_points.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.forecast_points.p10 IS '[I] Cuantil 10 %.';
COMMENT ON COLUMN ops.forecast_points.p50 IS '[I] Mediana (lo más probable).';
COMMENT ON COLUMN ops.forecast_points.p90 IS '[I] Cuantil 90 %.';

ALTER TABLE ops.forecast_points ADD CONSTRAINT forecast_points_forecast_run_id_fk
  FOREIGN KEY (forecast_run_id, aqueduct_id) REFERENCES ops.forecast_runs (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_points ADD CONSTRAINT forecast_points_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

CREATE INDEX forecast_points_aqueduct_id_idx ON ops.forecast_points (aqueduct_id);

REVOKE ALL ON ops.forecast_points FROM PUBLIC;
GRANT SELECT, INSERT ON ops.forecast_points TO caudal_app;
