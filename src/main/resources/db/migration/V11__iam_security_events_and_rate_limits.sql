-- V11: iam.security_events, iam.rate_limit_buckets.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE iam.security_events (
  id                         bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  aqueduct_id                uuid,
  type                       varchar(40) NOT NULL,
  severity                   varchar(10) NOT NULL,
  actor_user_id              uuid,
  device_id                  uuid,
  ip_hmac                    varchar(64),
  request_id                 varchar(40),
  details                    jsonb NOT NULL DEFAULT '{}',
  created_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT security_events_pk PRIMARY KEY (id),
  CONSTRAINT security_events_type_values CHECK (type IN ('ACCOUNT_LOCKED', 'TOKEN_REUSE_DETECTED', 'PERMISSION_DENIED', 'RATE_LIMITED', 'INVALID_SIGNATURE', 'NONCE_REPLAY', 'HONEYPOT_TRIGGERED', 'IMPORT_REJECTED', 'MFA_FAILED')),
  CONSTRAINT security_events_severity_values CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

COMMENT ON TABLE iam.security_events IS 'Eventos de seguridad para monitoreo (bloqueos, reutilización de token, firma inválida, etc.).';
COMMENT ON COLUMN iam.security_events.id IS '[I] Identificador.';
COMMENT ON COLUMN iam.security_events.aqueduct_id IS '[I] Acueducto si aplica.';
COMMENT ON COLUMN iam.security_events.type IS '[I] Tipo de evento.';
COMMENT ON COLUMN iam.security_events.severity IS '[I] Severidad.';
COMMENT ON COLUMN iam.security_events.actor_user_id IS '[I] Usuario involucrado.';
COMMENT ON COLUMN iam.security_events.device_id IS '[I] Dispositivo involucrado.';
COMMENT ON COLUMN iam.security_events.ip_hmac IS '[C] HMAC de la IP.';
COMMENT ON COLUMN iam.security_events.request_id IS '[I] Correlación con los logs.';
COMMENT ON COLUMN iam.security_events.details IS '[C] Detalles redactados (sin secretos).';
COMMENT ON COLUMN iam.security_events.created_at IS '[I] Momento.';

ALTER TABLE iam.security_events ADD CONSTRAINT security_events_actor_user_id_fk
  FOREIGN KEY (actor_user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX security_events_aqueduct_id_idx ON iam.security_events (aqueduct_id);
CREATE INDEX security_events_actor_user_id_idx ON iam.security_events (actor_user_id);
CREATE INDEX security_events_device_id_idx ON iam.security_events (device_id);
CREATE INDEX security_events_time_idx ON iam.security_events (created_at);

REVOKE ALL ON iam.security_events FROM PUBLIC;
GRANT SELECT, INSERT ON iam.security_events TO caudal_app;
GRANT USAGE ON SEQUENCE iam.security_events_id_seq TO caudal_app;

CREATE TABLE iam.rate_limit_buckets (
  id                         varchar(200) NOT NULL,
  state                      bytea NOT NULL,
  expires_at                 bigint,
  CONSTRAINT rate_limit_buckets_pk PRIMARY KEY (id)
);

COMMENT ON TABLE iam.rate_limit_buckets IS 'Estado de Bucket4j en PostgreSQL para límites compartidos entre instancias.';
COMMENT ON COLUMN iam.rate_limit_buckets.id IS '[I] Clave del bucket (tipo:identificador).';
COMMENT ON COLUMN iam.rate_limit_buckets.state IS '[I] Estado serializado por Bucket4j.';
COMMENT ON COLUMN iam.rate_limit_buckets.expires_at IS '[I] Expiración en milisegundos.';

REVOKE ALL ON iam.rate_limit_buckets FROM PUBLIC;
GRANT SELECT, INSERT ON iam.rate_limit_buckets TO caudal_app;
GRANT UPDATE (state, expires_at) ON iam.rate_limit_buckets TO caudal_app;
