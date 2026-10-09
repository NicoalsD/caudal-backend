-- V17: org.rule_sets.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.rule_sets (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  version                    integer NOT NULL,
  status                     varchar(12) NOT NULL DEFAULT 'DRAFT',
  based_on_rule_set_id       uuid,
  valid_from                 timestamptz,
  valid_to                   timestamptz,
  reserve_level              numeric(6,2) NOT NULL,
  max_daily_service_hours    numeric(4,2) NOT NULL,
  min_shift_hours            numeric(4,2) NOT NULL,
  max_shift_hours            numeric(4,2) NOT NULL,
  reserve_policy             varchar(30) NOT NULL DEFAULT 'REDUCE_TO_CRITICAL_BAND',
  allocation_strategy        varchar(40) NOT NULL DEFAULT 'PRIORITY_THEN_LONGEST_WAIT',
  duplicate_window_minutes   integer NOT NULL,
  max_level_change_per_hour  numeric(6,2) NOT NULL,
  stale_reading_hours        integer NOT NULL,
  max_backdate_days          integer NOT NULL,
  forecast_horizon_days      smallint NOT NULL,
  forecast_context_days      smallint NOT NULL,
  trend_threshold_per_day    numeric(6,2) NOT NULL,
  change_reason              varchar(${reason_max}),
  created_by                 uuid NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  activated_by               uuid,
  activated_at               timestamptz,
  CONSTRAINT rule_sets_pk PRIMARY KEY (id),
  CONSTRAINT rule_sets_version_range CHECK (version > 0),
  CONSTRAINT rule_sets_status_values CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'DISCARDED')),
  CONSTRAINT rule_sets_max_daily_service_hours_range CHECK (max_daily_service_hours > 0 AND max_daily_service_hours <= ${hours_per_day}),
  CONSTRAINT rule_sets_min_shift_hours_range CHECK (min_shift_hours > 0),
  CONSTRAINT rule_sets_max_shift_hours_range CHECK (max_shift_hours >= min_shift_hours),
  CONSTRAINT rule_sets_reserve_policy_values CHECK (reserve_policy IN ('NONE', 'REDUCE_TO_CRITICAL_BAND', 'PRIORITY_ONLY')),
  CONSTRAINT rule_sets_allocation_strategy_values CHECK (allocation_strategy IN ('PRIORITY_THEN_LONGEST_WAIT', 'EQUAL_SPLIT')),
  CONSTRAINT rule_sets_duplicate_window_minutes_range CHECK (duplicate_window_minutes > 0),
  CONSTRAINT rule_sets_max_level_change_per_hour_range CHECK (max_level_change_per_hour > 0),
  CONSTRAINT rule_sets_stale_reading_hours_range CHECK (stale_reading_hours > 0),
  CONSTRAINT rule_sets_max_backdate_days_range CHECK (max_backdate_days > 0),
  CONSTRAINT rule_sets_forecast_horizon_days_range CHECK (forecast_horizon_days BETWEEN ${forecast_horizon_days_min} AND ${forecast_horizon_days_max}),
  CONSTRAINT rule_sets_forecast_context_days_range CHECK (forecast_context_days > 0),
  CONSTRAINT rule_sets_trend_threshold_per_day_range CHECK (trend_threshold_per_day >= 0),
  CONSTRAINT rule_sets_change_reason_required CHECK (status = 'DRAFT' OR change_reason IS NOT NULL),
  CONSTRAINT rule_sets_change_reason_length CHECK (char_length(change_reason) >= ${reason_min}),
  CONSTRAINT rule_sets_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE org.rule_sets IS 'Versiones de las reglas de la Junta. Inmutables una vez activas (trigger).';
COMMENT ON COLUMN org.rule_sets.id IS '[I] Identificador.';
COMMENT ON COLUMN org.rule_sets.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN org.rule_sets.version IS '[P] Versión (única por acueducto).';
COMMENT ON COLUMN org.rule_sets.status IS '[P] Estado.';
COMMENT ON COLUMN org.rule_sets.based_on_rule_set_id IS '[I] Versión de la que se copió (Prototype).';
COMMENT ON COLUMN org.rule_sets.valid_from IS '[P] Inicio de vigencia.';
COMMENT ON COLUMN org.rule_sets.valid_to IS '[P] Fin de vigencia.';
COMMENT ON COLUMN org.rule_sets.reserve_level IS '[P] Reserva mínima en unidades de la regla.';
COMMENT ON COLUMN org.rule_sets.max_daily_service_hours IS '[P] Máximo de horas de servicio por día.';
COMMENT ON COLUMN org.rule_sets.min_shift_hours IS '[P] Duración mínima de un turno.';
COMMENT ON COLUMN org.rule_sets.max_shift_hours IS '[P] Duración máxima de un turno.';
COMMENT ON COLUMN org.rule_sets.reserve_policy IS '[P] Qué hacer si el p10 del pronóstico cae bajo la reserva.';
COMMENT ON COLUMN org.rule_sets.allocation_strategy IS '[P] Estrategia de asignación.';
COMMENT ON COLUMN org.rule_sets.duplicate_window_minutes IS '[I] Ventana para detectar lecturas repetidas.';
COMMENT ON COLUMN org.rule_sets.max_level_change_per_hour IS '[I] Salto brusco máximo.';
COMMENT ON COLUMN org.rule_sets.stale_reading_hours IS '[I] Horas tras las que un dato se considera viejo.';
COMMENT ON COLUMN org.rule_sets.max_backdate_days IS '[I] Días máximos hacia atrás para una lectura.';
COMMENT ON COLUMN org.rule_sets.forecast_horizon_days IS '[P] Horizonte de pronóstico (1 a 3).';
COMMENT ON COLUMN org.rule_sets.forecast_context_days IS '[I] Días de historia enviados a la IA.';
COMMENT ON COLUMN org.rule_sets.trend_threshold_per_day IS '[I] Umbral para decir "viene bajando".';
COMMENT ON COLUMN org.rule_sets.change_reason IS '[I] Motivo del cambio (se exige al activar).';
COMMENT ON COLUMN org.rule_sets.created_by IS '[I] Autor del borrador.';
COMMENT ON COLUMN org.rule_sets.created_at IS '[I] Creación.';
COMMENT ON COLUMN org.rule_sets.activated_by IS '[I] Quién la activó.';
COMMENT ON COLUMN org.rule_sets.activated_at IS '[I] Activación.';

ALTER TABLE org.rule_sets ADD CONSTRAINT rule_sets_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE org.rule_sets ADD CONSTRAINT rule_sets_based_on_rule_set_id_fk
  FOREIGN KEY (based_on_rule_set_id, aqueduct_id) REFERENCES org.rule_sets (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE org.rule_sets ADD CONSTRAINT rule_sets_created_by_fk
  FOREIGN KEY (created_by) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE org.rule_sets ADD CONSTRAINT rule_sets_activated_by_fk
  FOREIGN KEY (activated_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX rule_sets_aqueduct_id_idx ON org.rule_sets (aqueduct_id);
CREATE INDEX rule_sets_based_on_rule_set_id_idx ON org.rule_sets (based_on_rule_set_id);
CREATE INDEX rule_sets_created_by_idx ON org.rule_sets (created_by);
CREATE INDEX rule_sets_activated_by_idx ON org.rule_sets (activated_by);
ALTER TABLE org.rule_sets ADD CONSTRAINT rule_sets_version_uq UNIQUE (aqueduct_id, version);
CREATE UNIQUE INDEX rule_sets_one_active ON org.rule_sets (aqueduct_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX rule_sets_one_draft ON org.rule_sets (aqueduct_id) WHERE status = 'DRAFT';

REVOKE ALL ON org.rule_sets FROM PUBLIC;
GRANT SELECT, INSERT ON org.rule_sets TO caudal_app;
GRANT UPDATE (status, valid_from, valid_to, activated_by, activated_at, change_reason, reserve_level, max_daily_service_hours, min_shift_hours, max_shift_hours, reserve_policy, allocation_strategy, duplicate_window_minutes, max_level_change_per_hour, stale_reading_hours, max_backdate_days, forecast_horizon_days, forecast_context_days, trend_threshold_per_day) ON org.rule_sets TO caudal_app;
