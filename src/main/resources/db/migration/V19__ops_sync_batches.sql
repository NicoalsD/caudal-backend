-- V19: ops.sync_batches.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.sync_batches (
  id                         uuid NOT NULL,
  aqueduct_id                uuid NOT NULL,
  user_id                    uuid NOT NULL,
  received_at                timestamptz NOT NULL DEFAULT now(),
  items_received             integer NOT NULL,
  items_accepted             integer NOT NULL,
  items_rejected             integer NOT NULL,
  CONSTRAINT sync_batches_pk PRIMARY KEY (id),
  CONSTRAINT sync_batches_items_received_range CHECK (items_received >= 0),
  CONSTRAINT sync_batches_items_accepted_range CHECK (items_accepted >= 0),
  CONSTRAINT sync_batches_items_rejected_range CHECK (items_rejected >= 0),
  CONSTRAINT sync_batches_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.sync_batches IS 'Lotes de sincronización enviados por la app sin conexión.';
COMMENT ON COLUMN ops.sync_batches.id IS '[I] UUID generado por el cliente (idempotencia).';
COMMENT ON COLUMN ops.sync_batches.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.sync_batches.user_id IS '[I] Quién sincronizó.';
COMMENT ON COLUMN ops.sync_batches.received_at IS '[I] Recepción.';
COMMENT ON COLUMN ops.sync_batches.items_received IS '[I] Elementos recibidos.';
COMMENT ON COLUMN ops.sync_batches.items_accepted IS '[I] Aceptados.';
COMMENT ON COLUMN ops.sync_batches.items_rejected IS '[I] Rechazados.';

ALTER TABLE ops.sync_batches ADD CONSTRAINT sync_batches_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.sync_batches ADD CONSTRAINT sync_batches_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX sync_batches_aqueduct_id_idx ON ops.sync_batches (aqueduct_id);
CREATE INDEX sync_batches_user_id_idx ON ops.sync_batches (user_id);

REVOKE ALL ON ops.sync_batches FROM PUBLIC;
GRANT SELECT, INSERT ON ops.sync_batches TO caudal_app;
