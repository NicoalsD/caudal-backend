-- V13: org.tanks.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.tanks (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  name                       varchar(${place_name_max}) NOT NULL,
  gauge_min                  numeric(6,2) NOT NULL,
  gauge_max                  numeric(6,2) NOT NULL,
  gauge_step                 numeric(4,2) NOT NULL,
  capacity_liters            integer,
  is_active                  boolean NOT NULL DEFAULT true,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT tanks_pk PRIMARY KEY (id),
  CONSTRAINT tanks_name_length CHECK (char_length(name) >= ${place_name_min}),
  CONSTRAINT tanks_gauge_max_rule CHECK (gauge_max > gauge_min),
  CONSTRAINT tanks_gauge_step_rule CHECK (gauge_step > 0),
  CONSTRAINT tanks_capacity_liters_range CHECK (capacity_liters > 0),
  CONSTRAINT tanks_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE org.tanks IS 'Tanques y el rango de su regla pintada (fuente única del rango válido de una lectura).';
COMMENT ON COLUMN org.tanks.id IS '[I] Identificador.';
COMMENT ON COLUMN org.tanks.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN org.tanks.name IS '[P] Nombre.';
COMMENT ON COLUMN org.tanks.gauge_min IS '[P] Valor mínimo de la regla.';
COMMENT ON COLUMN org.tanks.gauge_max IS '[P] Valor máximo de la regla.';
COMMENT ON COLUMN org.tanks.gauge_step IS '[P] Precisión de lectura.';
COMMENT ON COLUMN org.tanks.capacity_liters IS '[I] Capacidad (opcional).';
COMMENT ON COLUMN org.tanks.is_active IS '[I] Activo.';
COMMENT ON COLUMN org.tanks.created_at IS '[I] Creación.';
COMMENT ON COLUMN org.tanks.updated_at IS '[I] Última modificación.';

ALTER TABLE org.tanks ADD CONSTRAINT tanks_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

CREATE INDEX tanks_aqueduct_id_idx ON org.tanks (aqueduct_id);

REVOKE ALL ON org.tanks FROM PUBLIC;
GRANT SELECT, INSERT ON org.tanks TO caudal_app;
GRANT UPDATE (name, capacity_liters, is_active, updated_at) ON org.tanks TO caudal_app;
