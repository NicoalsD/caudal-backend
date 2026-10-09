-- V12: org.aqueducts.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.aqueducts (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  slug                       varchar(${aqueduct_slug_max}) NOT NULL,
  name                       varchar(${aqueduct_name_max}) NOT NULL,
  municipality               varchar(60) NOT NULL,
  department                 varchar(60) NOT NULL,
  timezone                   varchar(40) NOT NULL DEFAULT 'America/Bogota',
  is_demo                    boolean NOT NULL DEFAULT false,
  public_page_enabled        boolean NOT NULL DEFAULT true,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT aqueducts_pk PRIMARY KEY (id),
  CONSTRAINT aqueducts_slug_uq UNIQUE (slug),
  CONSTRAINT aqueducts_slug_length CHECK (char_length(slug) >= ${aqueduct_slug_min}),
  CONSTRAINT aqueducts_slug_format CHECK (slug ~ '${aqueduct_slug_pattern}'),
  CONSTRAINT aqueducts_name_length CHECK (char_length(name) >= ${aqueduct_name_min})
);

COMMENT ON TABLE org.aqueducts IS 'Acueductos veredales. El acueducto demo (is_demo) contiene solo datos simulados.';
COMMENT ON COLUMN org.aqueducts.id IS '[P] Identificador.';
COMMENT ON COLUMN org.aqueducts.slug IS '[P] Identificador público para URLs.';
COMMENT ON COLUMN org.aqueducts.name IS '[P] Nombre.';
COMMENT ON COLUMN org.aqueducts.municipality IS '[P] Municipio (Guaitarilla).';
COMMENT ON COLUMN org.aqueducts.department IS '[P] Departamento (Nariño).';
COMMENT ON COLUMN org.aqueducts.timezone IS '[P] Zona horaria IANA.';
COMMENT ON COLUMN org.aqueducts.is_demo IS '[P] Acueducto con datos simulados.';
COMMENT ON COLUMN org.aqueducts.public_page_enabled IS '[P] Página pública activa.';
COMMENT ON COLUMN org.aqueducts.created_at IS '[I] Creación.';
COMMENT ON COLUMN org.aqueducts.updated_at IS '[I] Última modificación.';

REVOKE ALL ON org.aqueducts FROM PUBLIC;
GRANT SELECT ON org.aqueducts TO caudal_app;
GRANT UPDATE (name, public_page_enabled, timezone, updated_at) ON org.aqueducts TO caudal_app;

-- Foreign keys that waited for org.aqueducts.
ALTER TABLE iam.memberships ADD CONSTRAINT memberships_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE iam.security_events ADD CONSTRAINT security_events_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
