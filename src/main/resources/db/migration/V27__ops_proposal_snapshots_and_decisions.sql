-- V27: ops.proposal_snapshots, ops.proposal_decisions.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.proposal_snapshots (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  proposal_id                uuid NOT NULL,
  snapshot                   jsonb NOT NULL,
  taken_at                   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT proposal_snapshots_pk PRIMARY KEY (id),
  CONSTRAINT proposal_snapshots_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.proposal_snapshots IS 'Memento de la propuesta original antes de que la Junta la cambie.';
COMMENT ON COLUMN ops.proposal_snapshots.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.proposal_snapshots.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.proposal_snapshots.proposal_id IS '[I] Propuesta.';
COMMENT ON COLUMN ops.proposal_snapshots.snapshot IS '[I] Contenido completo.';
COMMENT ON COLUMN ops.proposal_snapshots.taken_at IS '[I] Momento.';

ALTER TABLE ops.proposal_snapshots ADD CONSTRAINT proposal_snapshots_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.proposal_snapshots ADD CONSTRAINT proposal_snapshots_proposal_id_fk
  FOREIGN KEY (proposal_id, aqueduct_id) REFERENCES ops.schedule_proposals (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX proposal_snapshots_aqueduct_id_idx ON ops.proposal_snapshots (aqueduct_id);
CREATE INDEX proposal_snapshots_proposal_id_idx ON ops.proposal_snapshots (proposal_id);

REVOKE ALL ON ops.proposal_snapshots FROM PUBLIC;
GRANT SELECT, INSERT ON ops.proposal_snapshots TO caudal_app;

CREATE TABLE ops.proposal_decisions (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  proposal_id                uuid NOT NULL,
  decision                   varchar(25) NOT NULL,
  reason                     varchar(${reason_max}),
  decided_by                 uuid NOT NULL,
  decided_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT proposal_decisions_pk PRIMARY KEY (id),
  CONSTRAINT proposal_decisions_decision_values CHECK (decision IN ('APPROVED', 'APPROVED_WITH_CHANGES', 'REJECTED')),
  CONSTRAINT proposal_decisions_reason_required CHECK (decision = 'APPROVED' OR reason IS NOT NULL),
  CONSTRAINT proposal_decisions_reason_length CHECK (char_length(reason) >= ${reason_min}),
  CONSTRAINT proposal_decisions_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.proposal_decisions IS 'Decisión de la Junta (append-only). Modificar o rechazar exige motivo.';
COMMENT ON COLUMN ops.proposal_decisions.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.proposal_decisions.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.proposal_decisions.proposal_id IS '[I] Propuesta.';
COMMENT ON COLUMN ops.proposal_decisions.decision IS '[I] Decisión.';
COMMENT ON COLUMN ops.proposal_decisions.reason IS '[I] Motivo.';
COMMENT ON COLUMN ops.proposal_decisions.decided_by IS '[C] Miembro de la Junta (nunca público).';
COMMENT ON COLUMN ops.proposal_decisions.decided_at IS '[I] Momento.';

ALTER TABLE ops.proposal_decisions ADD CONSTRAINT proposal_decisions_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.proposal_decisions ADD CONSTRAINT proposal_decisions_proposal_id_fk
  FOREIGN KEY (proposal_id, aqueduct_id) REFERENCES ops.schedule_proposals (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.proposal_decisions ADD CONSTRAINT proposal_decisions_decided_by_fk
  FOREIGN KEY (decided_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX proposal_decisions_aqueduct_id_idx ON ops.proposal_decisions (aqueduct_id);
CREATE INDEX proposal_decisions_proposal_id_idx ON ops.proposal_decisions (proposal_id);
CREATE INDEX proposal_decisions_decided_by_idx ON ops.proposal_decisions (decided_by);

REVOKE ALL ON ops.proposal_decisions FROM PUBLIC;
GRANT SELECT, INSERT ON ops.proposal_decisions TO caudal_app;
