-- V2: iam.users.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.users (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  username                   varchar(${username_max}) NOT NULL,
  full_name                  varchar(${person_name_max}) NOT NULL,
  password_hash              varchar(255) NOT NULL,
  status                     varchar(20) NOT NULL DEFAULT 'ACTIVE',
  must_change_password       boolean NOT NULL DEFAULT true,
  password_changed_at        timestamptz,
  failed_login_count         integer NOT NULL DEFAULT 0,
  locked_until               timestamptz,
  lockout_level              smallint NOT NULL DEFAULT 0,
  token_version              integer NOT NULL DEFAULT 0,
  last_login_at              timestamptz,
  last_login_ip_hmac         varchar(64),
  created_at                 timestamptz NOT NULL DEFAULT now(),
  created_by                 uuid,
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  disabled_at                timestamptz,
  CONSTRAINT users_pk PRIMARY KEY (id),
  CONSTRAINT users_username_uq UNIQUE (username),
  CONSTRAINT users_username_length CHECK (char_length(username) >= ${username_min}),
  CONSTRAINT users_username_format CHECK (username ~ '${username_pattern}'),
  CONSTRAINT users_full_name_length CHECK (char_length(full_name) >= ${person_name_min}),
  CONSTRAINT users_status_values CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
  CONSTRAINT users_failed_login_count_range CHECK (failed_login_count >= 0),
  CONSTRAINT users_lockout_level_range CHECK (lockout_level >= 0)
);

COMMENT ON TABLE iam.users IS 'Cuentas de personas que inician sesión (Junta, fontanero, equipo, entidades).';
COMMENT ON COLUMN iam.users.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.users.username IS '[I] Nombre de usuario para iniciar sesión.';
COMMENT ON COLUMN iam.users.full_name IS '[C] Nombre completo; nunca se muestra en lo público.';
COMMENT ON COLUMN iam.users.password_hash IS '[S] Hash Argon2id en formato PHC. Sin permiso de lectura para caudal_readonly.';
COMMENT ON COLUMN iam.users.status IS '[I] Estado de la cuenta.';
COMMENT ON COLUMN iam.users.must_change_password IS '[I] Obliga a cambiar la contraseña al ingresar.';
COMMENT ON COLUMN iam.users.password_changed_at IS '[I] Último cambio de contraseña.';
COMMENT ON COLUMN iam.users.failed_login_count IS '[I] Fallos consecutivos de inicio de sesión.';
COMMENT ON COLUMN iam.users.locked_until IS '[I] Fin del bloqueo temporal.';
COMMENT ON COLUMN iam.users.lockout_level IS '[I] Nivel de escalamiento del bloqueo.';
COMMENT ON COLUMN iam.users.token_version IS '[I] Se incrementa para invalidar todos los JWT.';
COMMENT ON COLUMN iam.users.last_login_at IS '[I] Último ingreso exitoso.';
COMMENT ON COLUMN iam.users.last_login_ip_hmac IS '[C] HMAC de la IP del último ingreso.';
COMMENT ON COLUMN iam.users.created_at IS '[I] Creación.';
COMMENT ON COLUMN iam.users.created_by IS '[I] Quién creó la cuenta.';
COMMENT ON COLUMN iam.users.updated_at IS '[I] Última modificación (trigger).';
COMMENT ON COLUMN iam.users.disabled_at IS '[I] Desactivación.';

ALTER TABLE iam.users ADD CONSTRAINT users_created_by_fk
  FOREIGN KEY (created_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX users_created_by_idx ON iam.users (created_by);

REVOKE ALL ON iam.users FROM PUBLIC;
