-- V6: iam.login_attempts, iam.password_history.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.login_attempts (
  id                         bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id                    uuid,
  username_hmac              varchar(64) NOT NULL,
  ip_hmac                    varchar(64) NOT NULL,
  succeeded                  boolean NOT NULL,
  failure_reason             varchar(30),
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT login_attempts_pk PRIMARY KEY (id),
  CONSTRAINT login_attempts_failure_reason_values CHECK (failure_reason IN ('INVALID_CREDENTIALS', 'LOCKED', 'DISABLED', 'RATE_LIMITED', 'MFA_FAILED'))
);

COMMENT ON TABLE iam.login_attempts IS 'Intentos de inicio de sesión para bloqueo y monitoreo (retención limitada).';
COMMENT ON COLUMN iam.login_attempts.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.login_attempts.user_id IS '[I] Usuario si existe.';
COMMENT ON COLUMN iam.login_attempts.username_hmac IS '[C] HMAC del usuario intentado (no se guarda texto arbitrario).';
COMMENT ON COLUMN iam.login_attempts.ip_hmac IS '[C] HMAC de la IP.';
COMMENT ON COLUMN iam.login_attempts.succeeded IS '[I] Resultado.';
COMMENT ON COLUMN iam.login_attempts.failure_reason IS '[I] Motivo del fallo.';
COMMENT ON COLUMN iam.login_attempts.created_at IS '[I] Momento.';

ALTER TABLE iam.login_attempts ADD CONSTRAINT login_attempts_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX login_attempts_user_id_idx ON iam.login_attempts (user_id);
CREATE INDEX login_attempts_ip_time_idx ON iam.login_attempts (ip_hmac, created_at);
CREATE INDEX login_attempts_user_time_idx ON iam.login_attempts (user_id, created_at);

REVOKE ALL ON iam.login_attempts FROM PUBLIC;
GRANT SELECT, INSERT ON iam.login_attempts TO caudal_app;
GRANT USAGE ON SEQUENCE iam.login_attempts_id_seq TO caudal_app;

CREATE TABLE iam.password_history (
  id                         bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id                    uuid NOT NULL,
  password_hash              varchar(255) NOT NULL,
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT password_history_pk PRIMARY KEY (id)
);

COMMENT ON TABLE iam.password_history IS 'Hashes anteriores para impedir reutilizar contraseñas.';
COMMENT ON COLUMN iam.password_history.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.password_history.user_id IS '[I] Usuario.';
COMMENT ON COLUMN iam.password_history.password_hash IS '[S] Hash Argon2id anterior.';
COMMENT ON COLUMN iam.password_history.created_at IS '[I] Momento.';

ALTER TABLE iam.password_history ADD CONSTRAINT password_history_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX password_history_user_id_idx ON iam.password_history (user_id);

REVOKE ALL ON iam.password_history FROM PUBLIC;
GRANT SELECT, INSERT ON iam.password_history TO caudal_app;
GRANT USAGE ON SEQUENCE iam.password_history_id_seq TO caudal_app;
