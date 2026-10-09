-- V35: audit.audit_log.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE audit.audit_log (
  id                         bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  aqueduct_id                uuid,
  occurred_at                timestamptz NOT NULL DEFAULT now(),
  actor_type                 varchar(10) NOT NULL,
  actor_id                   uuid,
  action                     varchar(60) NOT NULL,
  entity_type                varchar(60) NOT NULL,
  entity_id                  varchar(60) NOT NULL,
  before_state               jsonb,
  after_state                jsonb,
  request_id                 varchar(40),
  ip_hmac                    varchar(64),
  prev_hash                  char(64) NOT NULL,
  row_hash                   char(64) NOT NULL,
  CONSTRAINT audit_log_pk PRIMARY KEY (id),
  CONSTRAINT audit_log_actor_type_values CHECK (actor_type IN ('USER', 'DEVICE', 'SYSTEM', 'API_KEY')),
  CONSTRAINT audit_log_row_hash_uq UNIQUE (row_hash)
);

COMMENT ON TABLE audit.audit_log IS 'Registro append-only con cadena de hashes (cada fila incluye el hash de la anterior).';
COMMENT ON COLUMN audit.audit_log.id IS '[I] Identificador.';
COMMENT ON COLUMN audit.audit_log.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN audit.audit_log.occurred_at IS '[I] Momento.';
COMMENT ON COLUMN audit.audit_log.actor_type IS '[I] Tipo de actor.';
COMMENT ON COLUMN audit.audit_log.actor_id IS '[I] Actor.';
COMMENT ON COLUMN audit.audit_log.action IS '[I] Acción (RULESET_ACTIVATED, PROPOSAL_APPROVED, ...).';
COMMENT ON COLUMN audit.audit_log.entity_type IS '[I] Entidad.';
COMMENT ON COLUMN audit.audit_log.entity_id IS '[I] Identificador de la entidad.';
COMMENT ON COLUMN audit.audit_log.before_state IS '[C] Estado anterior redactado.';
COMMENT ON COLUMN audit.audit_log.after_state IS '[C] Estado nuevo redactado.';
COMMENT ON COLUMN audit.audit_log.request_id IS '[I] Correlación.';
COMMENT ON COLUMN audit.audit_log.ip_hmac IS '[C] HMAC de la IP.';
COMMENT ON COLUMN audit.audit_log.prev_hash IS '[I] Hash de la fila anterior del mismo acueducto.';
COMMENT ON COLUMN audit.audit_log.row_hash IS '[I] SHA-256 de prev_hash + contenido (trigger).';

ALTER TABLE audit.audit_log ADD CONSTRAINT audit_log_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

CREATE INDEX audit_log_aqueduct_id_idx ON audit.audit_log (aqueduct_id);
CREATE INDEX audit_log_chain_idx ON audit.audit_log (aqueduct_id, id DESC);

REVOKE ALL ON audit.audit_log FROM PUBLIC;
GRANT SELECT, INSERT ON audit.audit_log TO caudal_app;
GRANT USAGE ON SEQUENCE audit.audit_log_id_seq TO caudal_app;
