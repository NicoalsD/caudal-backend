-- V8: iam.mfa_factors, iam.mfa_recovery_codes.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.mfa_factors (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  user_id                    uuid NOT NULL,
  type                       varchar(10) NOT NULL DEFAULT 'TOTP',
  secret_ciphertext          bytea NOT NULL,
  key_id                     varchar(40) NOT NULL,
  confirmed_at               timestamptz,
  last_used_step             bigint,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT mfa_factors_pk PRIMARY KEY (id),
  CONSTRAINT mfa_factors_user_id_uq UNIQUE (user_id),
  CONSTRAINT mfa_factors_type_values CHECK (type IN ('TOTP'))
);

COMMENT ON TABLE iam.mfa_factors IS 'Segundo factor TOTP opcional.';
COMMENT ON COLUMN iam.mfa_factors.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.mfa_factors.user_id IS '[I] Usuario.';
COMMENT ON COLUMN iam.mfa_factors.type IS '[I] Tipo.';
COMMENT ON COLUMN iam.mfa_factors.secret_ciphertext IS '[S] Secreto cifrado con AES-256-GCM.';
COMMENT ON COLUMN iam.mfa_factors.key_id IS '[I] Identificador de la llave de cifrado.';
COMMENT ON COLUMN iam.mfa_factors.confirmed_at IS '[I] Confirmación.';
COMMENT ON COLUMN iam.mfa_factors.last_used_step IS '[I] Último paso TOTP usado (anti-repetición).';
COMMENT ON COLUMN iam.mfa_factors.created_at IS '[I] Creación.';

ALTER TABLE iam.mfa_factors ADD CONSTRAINT mfa_factors_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

REVOKE ALL ON iam.mfa_factors FROM PUBLIC;
GRANT SELECT, INSERT ON iam.mfa_factors TO caudal_app;
GRANT UPDATE (confirmed_at, last_used_step) ON iam.mfa_factors TO caudal_app;

CREATE TABLE iam.mfa_recovery_codes (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  user_id                    uuid NOT NULL,
  code_hash                  char(64) NOT NULL,
  used_at                    timestamptz,
  CONSTRAINT mfa_recovery_codes_pk PRIMARY KEY (id),
  CONSTRAINT mfa_recovery_codes_code_hash_uq UNIQUE (code_hash)
);

COMMENT ON TABLE iam.mfa_recovery_codes IS 'Códigos de respaldo del segundo factor.';
COMMENT ON COLUMN iam.mfa_recovery_codes.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.mfa_recovery_codes.user_id IS '[I] Usuario.';
COMMENT ON COLUMN iam.mfa_recovery_codes.code_hash IS '[S] SHA-256 del código.';
COMMENT ON COLUMN iam.mfa_recovery_codes.used_at IS '[I] Uso.';

ALTER TABLE iam.mfa_recovery_codes ADD CONSTRAINT mfa_recovery_codes_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX mfa_recovery_codes_user_id_idx ON iam.mfa_recovery_codes (user_id);

REVOKE ALL ON iam.mfa_recovery_codes FROM PUBLIC;
GRANT SELECT, INSERT ON iam.mfa_recovery_codes TO caudal_app;
GRANT UPDATE (used_at) ON iam.mfa_recovery_codes TO caudal_app;
