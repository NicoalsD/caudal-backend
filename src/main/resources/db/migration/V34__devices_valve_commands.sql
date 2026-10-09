-- V34: devices.valve_commands, devices.valve_command_events.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE devices.valve_commands (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  valve_id                   uuid NOT NULL,
  device_id                  uuid NOT NULL,
  command                    varchar(10) NOT NULL,
  schedule_item_id           uuid,
  manual_reason              varchar(${reason_max}),
  status                     varchar(12) NOT NULL DEFAULT 'PENDING',
  not_before                 timestamptz NOT NULL,
  expires_at                 timestamptz NOT NULL,
  created_by                 uuid,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT valve_commands_pk PRIMARY KEY (id),
  CONSTRAINT valve_commands_command_values CHECK (command IN ('OPEN', 'CLOSE')),
  CONSTRAINT valve_commands_origin_required CHECK (schedule_item_id IS NOT NULL OR manual_reason IS NOT NULL),
  CONSTRAINT valve_commands_status_values CHECK (status IN ('PENDING', 'DELIVERED', 'ACKED', 'FAILED', 'EXPIRED', 'CANCELLED')),
  CONSTRAINT valve_commands_expires_at_rule CHECK (expires_at > not_before),
  CONSTRAINT valve_commands_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE devices.valve_commands IS 'Comandos de válvula, solo desde turnos aprobados o manuales con motivo.';
COMMENT ON COLUMN devices.valve_commands.id IS '[I] Identificador.';
COMMENT ON COLUMN devices.valve_commands.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN devices.valve_commands.valve_id IS '[I] Válvula.';
COMMENT ON COLUMN devices.valve_commands.device_id IS '[I] Actuador.';
COMMENT ON COLUMN devices.valve_commands.command IS '[I] Acción.';
COMMENT ON COLUMN devices.valve_commands.schedule_item_id IS '[I] Turno aprobado que lo origina.';
COMMENT ON COLUMN devices.valve_commands.manual_reason IS '[I] Motivo de un comando manual.';
COMMENT ON COLUMN devices.valve_commands.status IS '[I] Estado.';
COMMENT ON COLUMN devices.valve_commands.not_before IS '[I] No ejecutar antes de.';
COMMENT ON COLUMN devices.valve_commands.expires_at IS '[I] Expiración.';
COMMENT ON COLUMN devices.valve_commands.created_by IS '[I] Quién lo creó (nulo si fue el sistema).';
COMMENT ON COLUMN devices.valve_commands.created_at IS '[I] Creación.';

ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_valve_id_fk
  FOREIGN KEY (valve_id) REFERENCES org.valves (id) ON DELETE RESTRICT;
ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_device_id_fk
  FOREIGN KEY (device_id, aqueduct_id) REFERENCES devices.devices (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_schedule_item_id_fk
  FOREIGN KEY (schedule_item_id, aqueduct_id) REFERENCES ops.schedule_items (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE devices.valve_commands ADD CONSTRAINT valve_commands_created_by_fk
  FOREIGN KEY (created_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX valve_commands_aqueduct_id_idx ON devices.valve_commands (aqueduct_id);
CREATE INDEX valve_commands_valve_id_idx ON devices.valve_commands (valve_id);
CREATE INDEX valve_commands_device_id_idx ON devices.valve_commands (device_id);
CREATE INDEX valve_commands_schedule_item_id_idx ON devices.valve_commands (schedule_item_id);
CREATE INDEX valve_commands_created_by_idx ON devices.valve_commands (created_by);

REVOKE ALL ON devices.valve_commands FROM PUBLIC;
GRANT SELECT, INSERT ON devices.valve_commands TO caudal_app;
GRANT UPDATE (status) ON devices.valve_commands TO caudal_app;

CREATE TABLE devices.valve_command_events (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  command_id                 uuid NOT NULL,
  event                      varchar(12) NOT NULL,
  detail                     varchar(200),
  occurred_at                timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT valve_command_events_pk PRIMARY KEY (id),
  CONSTRAINT valve_command_events_event_values CHECK (event IN ('DELIVERED', 'ACKED', 'FAILED', 'EXPIRED', 'CANCELLED')),
  CONSTRAINT valve_command_events_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE devices.valve_command_events IS 'Historial de entrega y confirmación de cada comando.';
COMMENT ON COLUMN devices.valve_command_events.id IS '[I] Identificador.';
COMMENT ON COLUMN devices.valve_command_events.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN devices.valve_command_events.command_id IS '[I] Comando.';
COMMENT ON COLUMN devices.valve_command_events.event IS '[I] Evento.';
COMMENT ON COLUMN devices.valve_command_events.detail IS '[I] Detalle.';
COMMENT ON COLUMN devices.valve_command_events.occurred_at IS '[I] Momento.';

ALTER TABLE devices.valve_command_events ADD CONSTRAINT valve_command_events_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE devices.valve_command_events ADD CONSTRAINT valve_command_events_command_id_fk
  FOREIGN KEY (command_id, aqueduct_id) REFERENCES devices.valve_commands (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX valve_command_events_aqueduct_id_idx ON devices.valve_command_events (aqueduct_id);
CREATE INDEX valve_command_events_command_id_idx ON devices.valve_command_events (command_id);

REVOKE ALL ON devices.valve_command_events FROM PUBLIC;
GRANT SELECT, INSERT ON devices.valve_command_events TO caudal_app;
