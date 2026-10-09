-- V7: iam.password_reset_grants.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.password_reset_grants (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  user_id                    uuid NOT NULL,
  code_hash                  char(64) NOT NULL,
  issued_by                  uuid NOT NULL,
  expires_at                 timestamptz NOT NULL,
  used_at                    timestamptz,
  revoked_at                 timestamptz,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT password_reset_grants_pk PRIMARY KEY (id),
  CONSTRAINT password_reset_grants_code_hash_uq UNIQUE (code_hash)
);

COMMENT ON TABLE iam.password_reset_grants IS 'Códigos de un solo uso que emite la Junta para restablecer una contraseña.';
COMMENT ON COLUMN iam.password_reset_grants.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.password_reset_grants.user_id IS '[I] Usuario beneficiado.';
COMMENT ON COLUMN iam.password_reset_grants.code_hash IS '[S] SHA-256 del código (el código se muestra una sola vez).';
COMMENT ON COLUMN iam.password_reset_grants.issued_by IS '[I] Quién lo emitió.';
COMMENT ON COLUMN iam.password_reset_grants.expires_at IS '[I] Expiración corta (configurable).';
COMMENT ON COLUMN iam.password_reset_grants.used_at IS '[I] Uso.';
COMMENT ON COLUMN iam.password_reset_grants.revoked_at IS '[I] Revocación.';
COMMENT ON COLUMN iam.password_reset_grants.created_at IS '[I] Creación.';

ALTER TABLE iam.password_reset_grants ADD CONSTRAINT password_reset_grants_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE iam.password_reset_grants ADD CONSTRAINT password_reset_grants_issued_by_fk
  FOREIGN KEY (issued_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX password_reset_grants_user_id_idx ON iam.password_reset_grants (user_id);
CREATE INDEX password_reset_grants_issued_by_idx ON iam.password_reset_grants (issued_by);

REVOKE ALL ON iam.password_reset_grants FROM PUBLIC;
GRANT SELECT, INSERT ON iam.password_reset_grants TO caudal_app;
GRANT UPDATE (used_at, revoked_at) ON iam.password_reset_grants TO caudal_app;
