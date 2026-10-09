-- V5: iam.refresh_tokens.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.refresh_tokens (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  user_id                    uuid NOT NULL,
  family_id                  uuid NOT NULL,
  token_hash                 char(64) NOT NULL,
  issued_at                  timestamptz NOT NULL DEFAULT now(),
  expires_at                 timestamptz NOT NULL,
  last_used_at               timestamptz,
  revoked_at                 timestamptz,
  revoked_reason             varchar(30),
  replaced_by_id             uuid,
  created_ip_hmac            varchar(64),
  user_agent                 varchar(200),
  CONSTRAINT refresh_tokens_pk PRIMARY KEY (id),
  CONSTRAINT refresh_tokens_token_hash_uq UNIQUE (token_hash),
  CONSTRAINT refresh_tokens_expires_at_rule CHECK (expires_at > issued_at),
  CONSTRAINT refresh_tokens_revoked_reason_values CHECK (revoked_reason IN ('ROTATED', 'REUSE_DETECTED', 'LOGOUT', 'LOGOUT_ALL', 'PASSWORD_CHANGED', 'ROLE_CHANGED', 'ADMIN'))
);

COMMENT ON TABLE iam.refresh_tokens IS 'Sesiones (refresh tokens opacos con rotación y detección de reutilización).';
COMMENT ON COLUMN iam.refresh_tokens.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.refresh_tokens.user_id IS '[I] Dueño de la sesión.';
COMMENT ON COLUMN iam.refresh_tokens.family_id IS '[I] Familia de rotación.';
COMMENT ON COLUMN iam.refresh_tokens.token_hash IS '[S] SHA-256 del token; el token nunca se guarda.';
COMMENT ON COLUMN iam.refresh_tokens.issued_at IS '[I] Emisión.';
COMMENT ON COLUMN iam.refresh_tokens.expires_at IS '[I] Expiración.';
COMMENT ON COLUMN iam.refresh_tokens.last_used_at IS '[I] Último uso.';
COMMENT ON COLUMN iam.refresh_tokens.revoked_at IS '[I] Revocación.';
COMMENT ON COLUMN iam.refresh_tokens.revoked_reason IS '[I] Motivo.';
COMMENT ON COLUMN iam.refresh_tokens.replaced_by_id IS '[I] Token que lo reemplazó.';
COMMENT ON COLUMN iam.refresh_tokens.created_ip_hmac IS '[C] HMAC de la IP.';
COMMENT ON COLUMN iam.refresh_tokens.user_agent IS '[I] Agente de usuario recortado.';

ALTER TABLE iam.refresh_tokens ADD CONSTRAINT refresh_tokens_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE iam.refresh_tokens ADD CONSTRAINT refresh_tokens_replaced_by_id_fk
  FOREIGN KEY (replaced_by_id) REFERENCES iam.refresh_tokens (id) ON DELETE RESTRICT;

CREATE INDEX refresh_tokens_user_id_idx ON iam.refresh_tokens (user_id);
CREATE INDEX refresh_tokens_replaced_by_id_idx ON iam.refresh_tokens (replaced_by_id);
CREATE INDEX refresh_tokens_family_idx ON iam.refresh_tokens (family_id);

REVOKE ALL ON iam.refresh_tokens FROM PUBLIC;
GRANT SELECT, INSERT ON iam.refresh_tokens TO caudal_app;
GRANT UPDATE (last_used_at, revoked_at, revoked_reason, replaced_by_id) ON iam.refresh_tokens TO caudal_app;
