-- V15: org.valves.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.valves (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  sector_id                  uuid NOT NULL,
  code                       varchar(${valve_code_max}) NOT NULL,
  name                       varchar(${place_name_max}) NOT NULL,
  location_hint              varchar(${location_hint_max}),
  is_motorized               boolean NOT NULL DEFAULT false,
  fail_safe_position         varchar(10) NOT NULL DEFAULT 'KEEP',
  is_active                  boolean NOT NULL DEFAULT true,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT valves_pk PRIMARY KEY (id),
  CONSTRAINT valves_code_length CHECK (char_length(code) >= ${valve_code_min}),
  CONSTRAINT valves_code_format CHECK (code ~ '${valve_code_pattern}'),
  CONSTRAINT valves_name_length CHECK (char_length(name) >= ${place_name_min}),
  CONSTRAINT valves_fail_safe_position_values CHECK (fail_safe_position IN ('KEEP', 'OPEN', 'CLOSED'))
);

COMMENT ON TABLE org.valves IS 'Válvulas físicas de cada sector.';
COMMENT ON COLUMN org.valves.id IS '[I] Identificador.';
COMMENT ON COLUMN org.valves.sector_id IS '[I] Sector.';
COMMENT ON COLUMN org.valves.code IS '[I] Código, único por sector.';
COMMENT ON COLUMN org.valves.name IS '[I] Nombre.';
COMMENT ON COLUMN org.valves.location_hint IS '[I] Referencia de ubicación.';
COMMENT ON COLUMN org.valves.is_motorized IS '[I] Tiene actuador.';
COMMENT ON COLUMN org.valves.fail_safe_position IS '[I] Posición segura ante fallas.';
COMMENT ON COLUMN org.valves.is_active IS '[I] Activa.';
COMMENT ON COLUMN org.valves.created_at IS '[I] Creación.';

ALTER TABLE org.valves ADD CONSTRAINT valves_sector_id_fk
  FOREIGN KEY (sector_id) REFERENCES org.sectors (id) ON DELETE RESTRICT;

CREATE INDEX valves_sector_id_idx ON org.valves (sector_id);
ALTER TABLE org.valves ADD CONSTRAINT valves_code_uq UNIQUE (sector_id, code);

REVOKE ALL ON org.valves FROM PUBLIC;
GRANT SELECT, INSERT ON org.valves TO caudal_app;
GRANT UPDATE (name, location_hint, is_motorized, fail_safe_position, is_active) ON org.valves TO caudal_app;
