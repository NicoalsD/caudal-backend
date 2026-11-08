-- V54: autopilot (v2). Three parts, one per unit of work of the plan:
--   1. ops.autopilot_decisions: every recommendation of the autopilot with its source and outcome.
--   2. devices.valve_commands.autopilot_decision_id and the rule of a single origin per command.
--   3. org.autopilot_settings: mode, operator absence time and reserve floor, one row per aqueduct.
-- Lengths and patterns come from FieldLimits through Flyway placeholders.

-- 1. ops.autopilot_decisions (B198) ----------------------------------------------------------
CREATE TABLE ops.autopilot_decisions (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  source                     varchar(5) NOT NULL,
  model                      varchar(${autopilot_model_max}) NOT NULL,
  action                     varchar(10) NOT NULL,
  valve_id                   uuid,
  reason                     varchar(${autopilot_reason_max}) NOT NULL,
  confidence                 numeric(4,3) NOT NULL,
  status                     varchar(10) NOT NULL DEFAULT 'PENDING',
  status_detail              varchar(${autopilot_detail_max}),
  decided_by                 uuid,
  decided_at                 timestamptz,
  inputs                     jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT autopilot_decisions_pk PRIMARY KEY (id),
  CONSTRAINT autopilot_decisions_source_values CHECK (source IN ('LLM', 'RULES')),
  CONSTRAINT autopilot_decisions_action_values CHECK (action IN ('OPEN', 'CLOSE', 'WAIT', 'INSPECT')),
  CONSTRAINT autopilot_decisions_status_values CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXECUTED', 'EXPIRED', 'SKIPPED')),
  CONSTRAINT autopilot_decisions_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
  CONSTRAINT autopilot_decisions_valve_required CHECK (action NOT IN ('OPEN', 'CLOSE') OR valve_id IS NOT NULL),
  CONSTRAINT autopilot_decisions_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.autopilot_decisions IS 'Decisiones del piloto automático: recomendación, origen (LLM o reglas), estado y quién la resolvió.';
COMMENT ON COLUMN ops.autopilot_decisions.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.autopilot_decisions.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.autopilot_decisions.source IS '[I] Quién la produjo: LLM o RULES (reglas deterministas).';
COMMENT ON COLUMN ops.autopilot_decisions.model IS '[I] Modelo o versión de reglas que la produjo.';
COMMENT ON COLUMN ops.autopilot_decisions.action IS '[I] OPEN, CLOSE, WAIT o INSPECT.';
COMMENT ON COLUMN ops.autopilot_decisions.valve_id IS '[I] Válvula de la acción (obligatoria en OPEN y CLOSE).';
COMMENT ON COLUMN ops.autopilot_decisions.reason IS '[I] Motivo en español.';
COMMENT ON COLUMN ops.autopilot_decisions.confidence IS '[I] Confianza de 0 a 1.';
COMMENT ON COLUMN ops.autopilot_decisions.status IS '[I] PENDING, ACCEPTED, REJECTED, EXECUTED, EXPIRED o SKIPPED.';
COMMENT ON COLUMN ops.autopilot_decisions.status_detail IS '[I] Por qué se omitió o rechazó.';
COMMENT ON COLUMN ops.autopilot_decisions.decided_by IS '[I] Persona que la aceptó o rechazó (nulo si la resolvió el piloto).';
COMMENT ON COLUMN ops.autopilot_decisions.decided_at IS '[I] Momento en que se resolvió.';
COMMENT ON COLUMN ops.autopilot_decisions.inputs IS '[I] Foto del estado que se envió al asistente.';
COMMENT ON COLUMN ops.autopilot_decisions.created_at IS '[I] Creación.';

ALTER TABLE ops.autopilot_decisions ADD CONSTRAINT autopilot_decisions_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.autopilot_decisions ADD CONSTRAINT autopilot_decisions_valve_id_fk
  FOREIGN KEY (valve_id) REFERENCES org.valves (id) ON DELETE RESTRICT;
ALTER TABLE ops.autopilot_decisions ADD CONSTRAINT autopilot_decisions_decided_by_fk
  FOREIGN KEY (decided_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX autopilot_decisions_aqueduct_created_idx ON ops.autopilot_decisions (aqueduct_id, created_at DESC);
CREATE INDEX autopilot_decisions_aqueduct_status_idx ON ops.autopilot_decisions (aqueduct_id, status);
CREATE INDEX autopilot_decisions_valve_id_idx ON ops.autopilot_decisions (valve_id);
CREATE INDEX autopilot_decisions_decided_by_idx ON ops.autopilot_decisions (decided_by);

REVOKE ALL ON ops.autopilot_decisions FROM PUBLIC;
GRANT SELECT, INSERT ON ops.autopilot_decisions TO caudal_app;
GRANT UPDATE (status, status_detail, decided_by, decided_at) ON ops.autopilot_decisions TO caudal_app;

ALTER TABLE ops.autopilot_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE ops.autopilot_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant ON ops.autopilot_decisions
  USING (aqueduct_id = audit.current_aqueduct_id())
  WITH CHECK (aqueduct_id = audit.current_aqueduct_id());

-- 2. devices.valve_commands.autopilot_decision_id (B199) and a single origin (B200) ----------
ALTER TABLE devices.valve_commands ADD COLUMN autopilot_decision_id uuid;
COMMENT ON COLUMN devices.valve_commands.autopilot_decision_id IS '[I] Decisión del piloto que lo origina.';

ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_autopilot_decision_id_fk
  FOREIGN KEY (autopilot_decision_id, aqueduct_id)
  REFERENCES ops.autopilot_decisions (id, aqueduct_id) ON DELETE RESTRICT;
CREATE INDEX valve_commands_autopilot_decision_id_idx ON devices.valve_commands (autopilot_decision_id);

-- A command has exactly one origin: an approved shift, a manual reason or an autopilot decision.
-- The V52 index (one active command per valve and not_before) is not touched.
ALTER TABLE devices.valve_commands DROP CONSTRAINT valve_commands_origin_required;
ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_origin_required
  CHECK (num_nonnulls(schedule_item_id, manual_reason, autopilot_decision_id) = 1);

COMMENT ON TABLE devices.valve_commands IS 'Comandos de válvula, con un solo origen: turno aprobado, comando manual con motivo o decisión del piloto.';

-- 3. org.autopilot_settings (B202) ----------------------------------------------------------
-- The defaults live here, in the columns: a row is created on first use with them.
CREATE TABLE org.autopilot_settings (
  aqueduct_id                uuid NOT NULL,
  mode                       varchar(10) NOT NULL DEFAULT 'ASSISTED',
  operator_absence_minutes   integer NOT NULL DEFAULT 30,
  reserve_floor_pct          numeric(5,2) NOT NULL DEFAULT 25.00,
  updated_by                 uuid,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT autopilot_settings_pk PRIMARY KEY (aqueduct_id),
  CONSTRAINT autopilot_settings_mode_values CHECK (mode IN ('MANUAL', 'ASSISTED', 'AUTOMATIC')),
  CONSTRAINT autopilot_settings_absence_range CHECK (operator_absence_minutes BETWEEN ${operator_absence_minutes_min} AND ${operator_absence_minutes_max}),
  CONSTRAINT autopilot_settings_floor_range CHECK (reserve_floor_pct BETWEEN ${reserve_floor_pct_min} AND ${reserve_floor_pct_max})
);

COMMENT ON TABLE org.autopilot_settings IS 'Configuración del piloto automático por acueducto: modo, ausencia del operario y piso de reserva.';
COMMENT ON COLUMN org.autopilot_settings.aqueduct_id IS '[I] Acueducto (una fila por acueducto).';
COMMENT ON COLUMN org.autopilot_settings.mode IS '[I] MANUAL, ASSISTED o AUTOMATIC.';
COMMENT ON COLUMN org.autopilot_settings.operator_absence_minutes IS '[I] Minutos sin acción humana antes de que AUTOMATIC actúe.';
COMMENT ON COLUMN org.autopilot_settings.reserve_floor_pct IS '[I] Nivel del tanque, en %, en o bajo el cual nunca se abre.';
COMMENT ON COLUMN org.autopilot_settings.updated_by IS '[I] Quién la cambió por última vez.';
COMMENT ON COLUMN org.autopilot_settings.created_at IS '[I] Creación.';
COMMENT ON COLUMN org.autopilot_settings.updated_at IS '[I] Última actualización.';

ALTER TABLE org.autopilot_settings ADD CONSTRAINT autopilot_settings_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE org.autopilot_settings ADD CONSTRAINT autopilot_settings_updated_by_fk
  FOREIGN KEY (updated_by) REFERENCES iam.users (id) ON DELETE RESTRICT;
CREATE INDEX autopilot_settings_updated_by_idx ON org.autopilot_settings (updated_by);

CREATE TRIGGER set_updated_at BEFORE UPDATE ON org.autopilot_settings
  FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();

-- Existing aqueducts start with the defaults of the columns. This runs before row level security
-- is turned on for the table, which applies to its owner too.
INSERT INTO org.autopilot_settings (aqueduct_id) SELECT id FROM org.aqueducts;

REVOKE ALL ON org.autopilot_settings FROM PUBLIC;
GRANT SELECT, INSERT ON org.autopilot_settings TO caudal_app;
GRANT UPDATE (mode, operator_absence_minutes, reserve_floor_pct, updated_by, updated_at)
  ON org.autopilot_settings TO caudal_app;

ALTER TABLE org.autopilot_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE org.autopilot_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant ON org.autopilot_settings
  USING (aqueduct_id = audit.current_aqueduct_id())
  WITH CHECK (aqueduct_id = audit.current_aqueduct_id());
