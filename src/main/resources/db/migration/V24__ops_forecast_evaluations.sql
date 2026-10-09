-- V24: ops.forecast_evaluations.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.forecast_evaluations (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  forecast_run_id            uuid NOT NULL,
  target_date                date NOT NULL,
  actual_reading_id          uuid NOT NULL,
  actual_value               numeric(6,2) NOT NULL,
  abs_error                  numeric(6,2) NOT NULL,
  within_interval            boolean NOT NULL,
  evaluated_at               timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT forecast_evaluations_pk PRIMARY KEY (id),
  CONSTRAINT forecast_evaluations_abs_error_range CHECK (abs_error >= 0),
  CONSTRAINT forecast_evaluations_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.forecast_evaluations IS 'Error de cada punto cuando llega el dato real (IA y estimación simple).';
COMMENT ON COLUMN ops.forecast_evaluations.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.forecast_evaluations.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.forecast_evaluations.forecast_run_id IS '[I] Pronóstico.';
COMMENT ON COLUMN ops.forecast_evaluations.target_date IS '[I] Día.';
COMMENT ON COLUMN ops.forecast_evaluations.actual_reading_id IS '[I] Lectura real usada.';
COMMENT ON COLUMN ops.forecast_evaluations.actual_value IS '[I] Valor real.';
COMMENT ON COLUMN ops.forecast_evaluations.abs_error IS '[I] Error absoluto del p50.';
COMMENT ON COLUMN ops.forecast_evaluations.within_interval IS '[I] El real cayó entre p10 y p90.';
COMMENT ON COLUMN ops.forecast_evaluations.evaluated_at IS '[I] Momento.';

ALTER TABLE ops.forecast_evaluations ADD CONSTRAINT forecast_evaluations_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_evaluations ADD CONSTRAINT forecast_evaluations_forecast_run_id_fk
  FOREIGN KEY (forecast_run_id, aqueduct_id) REFERENCES ops.forecast_runs (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.forecast_evaluations ADD CONSTRAINT forecast_evaluations_actual_reading_id_fk
  FOREIGN KEY (actual_reading_id, aqueduct_id) REFERENCES ops.readings (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX forecast_evaluations_aqueduct_id_idx ON ops.forecast_evaluations (aqueduct_id);
CREATE INDEX forecast_evaluations_forecast_run_id_idx ON ops.forecast_evaluations (forecast_run_id);
CREATE INDEX forecast_evaluations_actual_reading_id_idx ON ops.forecast_evaluations (actual_reading_id);
ALTER TABLE ops.forecast_evaluations ADD CONSTRAINT forecast_evaluations_point_uq UNIQUE (forecast_run_id, target_date);

REVOKE ALL ON ops.forecast_evaluations FROM PUBLIC;
GRANT SELECT, INSERT ON ops.forecast_evaluations TO caudal_app;
