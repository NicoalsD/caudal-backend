-- V9: iam.api_keys.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.api_keys (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  name                       varchar(60) NOT NULL,
  key_prefix                 varchar(12) NOT NULL,
  key_hash                   char(64) NOT NULL,
  scopes                     varchar(200) NOT NULL,
  expires_at                 timestamptz NOT NULL,
  last_used_at               timestamptz,
  revoked_at                 timestamptz,
  created_by                 uuid NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT api_keys_pk PRIMARY KEY (id),
  CONSTRAINT api_keys_name_uq UNIQUE (name),
  CONSTRAINT api_keys_key_hash_uq UNIQUE (key_hash)
);

COMMENT ON TABLE iam.api_keys IS 'Llaves para tareas internas (por ejemplo, cron de evaluación).';
COMMENT ON COLUMN iam.api_keys.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.api_keys.name IS '[I] Nombre descriptivo.';
COMMENT ON COLUMN iam.api_keys.key_prefix IS '[I] Prefijo visible para identificarla.';
COMMENT ON COLUMN iam.api_keys.key_hash IS '[S] SHA-256 de la llave.';
COMMENT ON COLUMN iam.api_keys.scopes IS '[I] Alcances separados por espacio.';
COMMENT ON COLUMN iam.api_keys.expires_at IS '[I] Expiración obligatoria.';
COMMENT ON COLUMN iam.api_keys.last_used_at IS '[I] Último uso.';
COMMENT ON COLUMN iam.api_keys.revoked_at IS '[I] Revocación.';
COMMENT ON COLUMN iam.api_keys.created_by IS '[I] Creador.';
COMMENT ON COLUMN iam.api_keys.created_at IS '[I] Creación.';

ALTER TABLE iam.api_keys ADD CONSTRAINT api_keys_created_by_fk
  FOREIGN KEY (created_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX api_keys_created_by_idx ON iam.api_keys (created_by);

REVOKE ALL ON iam.api_keys FROM PUBLIC;
GRANT SELECT, INSERT ON iam.api_keys TO caudal_app;
GRANT UPDATE (last_used_at, revoked_at) ON iam.api_keys TO caudal_app;
