-- V14: org.sectors.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.sectors (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  parent_sector_id           uuid,
  code                       varchar(${sector_code_max}) NOT NULL,
  name                       varchar(${place_name_max}) NOT NULL,
  households_count           integer NOT NULL DEFAULT 0,
  is_active                  boolean NOT NULL DEFAULT true,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT sectors_pk PRIMARY KEY (id),
  CONSTRAINT sectors_code_length CHECK (char_length(code) >= ${sector_code_min}),
  CONSTRAINT sectors_code_format CHECK (code ~ '${sector_code_pattern}'),
  CONSTRAINT sectors_name_length CHECK (char_length(name) >= ${place_name_min}),
  CONSTRAINT sectors_households_count_range CHECK (households_count >= 0),
  CONSTRAINT sectors_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE org.sectors IS 'Sectores que reciben agua por turnos (jerarquía para el patrón Composite). Sin datos personales.';
COMMENT ON COLUMN org.sectors.id IS '[P] Identificador.';
COMMENT ON COLUMN org.sectors.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN org.sectors.parent_sector_id IS '[P] Sector padre.';
COMMENT ON COLUMN org.sectors.code IS '[P] Código corto, único por acueducto.';
COMMENT ON COLUMN org.sectors.name IS '[P] Nombre visible.';
COMMENT ON COLUMN org.sectors.households_count IS '[I] Número de hogares (agregado).';
COMMENT ON COLUMN org.sectors.is_active IS '[P] Activo.';
COMMENT ON COLUMN org.sectors.created_at IS '[I] Creación.';
COMMENT ON COLUMN org.sectors.updated_at IS '[I] Última modificación.';

ALTER TABLE org.sectors ADD CONSTRAINT sectors_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE org.sectors ADD CONSTRAINT sectors_parent_sector_id_fk
  FOREIGN KEY (parent_sector_id, aqueduct_id) REFERENCES org.sectors (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX sectors_aqueduct_id_idx ON org.sectors (aqueduct_id);
CREATE INDEX sectors_parent_sector_id_idx ON org.sectors (parent_sector_id);
ALTER TABLE org.sectors ADD CONSTRAINT sectors_code_uq UNIQUE (aqueduct_id, code);

REVOKE ALL ON org.sectors FROM PUBLIC;
GRANT SELECT, INSERT ON org.sectors TO caudal_app;
GRANT UPDATE (name, households_count, parent_sector_id, is_active, updated_at) ON org.sectors TO caudal_app;
